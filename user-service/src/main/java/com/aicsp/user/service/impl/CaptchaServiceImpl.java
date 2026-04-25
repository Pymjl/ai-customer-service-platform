package com.aicsp.user.service.impl;

import com.aicsp.user.dto.auth.CaptchaChallengeResponse;
import com.aicsp.user.dto.auth.CaptchaVerifyResponse;
import com.aicsp.user.service.CaptchaService;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CaptchaServiceImpl implements CaptchaService {
    private static final String CHALLENGE_PREFIX = "captcha:challenge:";
    private static final String TOKEN_PREFIX = "captcha:token:";
    private static final String BACKGROUND_PATTERN = "classpath*:/captcha/backgrounds/*";
    private static final int WIDTH = 320;
    private static final int HEIGHT = 160;
    private static final int PIECE_SIZE = 52;
    private static final int TOLERANCE = 6;
    private static final int MIN_PIECE_X = 64;
    private static final int PIECE_EDGE_PADDING = 12;

    private final StringRedisTemplate redisTemplate;
    private final ResourcePatternResolver resourcePatternResolver;
    private final SecureRandom random = new SecureRandom();
    private final List<Resource> backgroundResources;

    public CaptchaServiceImpl(StringRedisTemplate redisTemplate, ResourcePatternResolver resourcePatternResolver) {
        this.redisTemplate = redisTemplate;
        this.resourcePatternResolver = resourcePatternResolver;
        this.backgroundResources = loadBackgroundResources();
    }

    @Override
    public CaptchaChallengeResponse createChallenge() {
        String id = UUID.randomUUID().toString();
        int x = randomPieceX();
        int y = randomPieceY();
        redisTemplate.opsForValue().set(CHALLENGE_PREFIX + id, String.valueOf(x), Duration.ofMinutes(3));

        BufferedImage source = sourceImage();
        return new CaptchaChallengeResponse(
                id,
                dataUrl(backgroundImage(copy(source), x, y)),
                dataUrl(sliderImage(source, x, y)),
                WIDTH,
                HEIGHT,
                PIECE_SIZE,
                y,
                180
        );
    }

    @Override
    public CaptchaVerifyResponse verify(String challengeId, int x) {
        String key = CHALLENGE_PREFIX + challengeId;
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null || Math.abs(Integer.parseInt(expected) - x) > TOLERANCE) {
            throw new IllegalArgumentException("滑块验证失败");
        }
        redisTemplate.delete(key);
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, "1", Duration.ofMinutes(5));
        return new CaptchaVerifyResponse(token);
    }

    @Override
    public void consumeToken(String token) {
        Boolean deleted = redisTemplate.delete(TOKEN_PREFIX + token);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new IllegalArgumentException("验证码无效或已过期");
        }
    }

    private BufferedImage backgroundImage(BufferedImage image, int x, int y) {
        Graphics2D graphics = image.createGraphics();
        enableQuality(graphics);
        Shape shape = puzzleShape(x, y);
        graphics.setColor(new Color(248, 250, 252, 205));
        graphics.fill(shape);
        graphics.setStroke(new BasicStroke(2f));
        graphics.setColor(new Color(15, 23, 42, 80));
        graphics.draw(shape);
        graphics.setColor(new Color(255, 255, 255, 160));
        graphics.draw(puzzleShape(x - 1, y - 1));
        graphics.dispose();
        return image;
    }

    private BufferedImage sliderImage(BufferedImage source, int x, int y) {
        BufferedImage cropped = source.getSubimage(x, y, PIECE_SIZE, PIECE_SIZE);
        BufferedImage piece = new BufferedImage(PIECE_SIZE, PIECE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = piece.createGraphics();
        enableQuality(graphics);
        Shape shape = puzzleShape(0, 0);
        graphics.setClip(shape);
        graphics.drawImage(cropped, 0, 0, null);
        graphics.setClip(null);
        graphics.setStroke(new BasicStroke(2f));
        graphics.setColor(new Color(255, 255, 255, 230));
        graphics.draw(shape);
        graphics.setColor(new Color(15, 23, 42, 85));
        graphics.draw(puzzleShape(1, 1));
        graphics.dispose();
        return piece;
    }

    private BufferedImage sourceImage() {
        if (!backgroundResources.isEmpty()) {
            int start = random.nextInt(backgroundResources.size());
            for (int i = 0; i < backgroundResources.size(); i++) {
                Resource resource = backgroundResources.get((start + i) % backgroundResources.size());
                try (InputStream inputStream = resource.getInputStream()) {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image != null) {
                        return preprocessImage(cropToCaptchaSize(image));
                    }
                } catch (Exception ignored) {
                    // Try the next image in the local gallery.
                }
            }
        }
        return generatedSourceImage();
    }

    private List<Resource> loadBackgroundResources() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(BACKGROUND_PATTERN);
            List<Resource> images = new ArrayList<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String lowerName = filename.toLowerCase(Locale.ROOT);
                if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png")) {
                    images.add(resource);
                }
            }
            return Collections.unmodifiableList(images);
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private BufferedImage cropToCaptchaSize(BufferedImage source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double targetRatio = WIDTH / (double) HEIGHT;
        double sourceRatio = sourceWidth / (double) sourceHeight;

        int cropWidth = sourceWidth;
        int cropHeight = sourceHeight;
        int cropX = 0;
        int cropY = 0;

        if (sourceRatio > targetRatio) {
            cropWidth = Math.max(1, (int) Math.round(sourceHeight * targetRatio));
            cropX = random.nextInt(sourceWidth - cropWidth + 1);
        } else if (sourceRatio < targetRatio) {
            cropHeight = Math.max(1, (int) Math.round(sourceWidth / targetRatio));
            cropY = random.nextInt(sourceHeight - cropHeight + 1);
        }

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        enableQuality(graphics);
        graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, cropX, cropY, cropX + cropWidth, cropY + cropHeight, null);
        graphics.dispose();
        return image;
    }

    private BufferedImage preprocessImage(BufferedImage source) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        float contrast = 0.88f + random.nextFloat() * 0.28f;
        int brightness = random.nextInt(25) - 12;

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int rgb = source.getRGB(x, y);
                int noise = random.nextInt(17) - 8;
                int red = adjustColor((rgb >> 16) & 0xff, contrast, brightness, noise);
                int green = adjustColor((rgb >> 8) & 0xff, contrast, brightness, noise);
                int blue = adjustColor(rgb & 0xff, contrast, brightness, noise);
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }

        Graphics2D graphics = image.createGraphics();
        enableQuality(graphics);
        addNoiseTexture(graphics);
        graphics.dispose();
        return image;
    }

    private int adjustColor(int value, float contrast, int brightness, int noise) {
        return clamp((int) ((value - 128) * contrast + 128 + brightness + noise));
    }

    private void addNoiseTexture(Graphics2D graphics) {
        for (int i = 0; i < 220; i++) {
            int alpha = 10 + random.nextInt(24);
            int gray = random.nextBoolean() ? 255 : 15;
            graphics.setColor(new Color(gray, gray, gray, alpha));
            int size = 1 + random.nextInt(2);
            graphics.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), size, size);
        }

        for (int i = 0; i < 6; i++) {
            graphics.setStroke(new BasicStroke(1f + random.nextFloat()));
            graphics.setColor(new Color(15, 23, 42, 14 + random.nextInt(22)));
            graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
    }

    private BufferedImage generatedSourceImage() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        enableQuality(graphics);
        graphics.setPaint(new GradientPaint(0, 0, new Color(219, 234, 254), WIDTH, HEIGHT, new Color(14, 165, 233)));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setPaint(new GradientPaint(40, 10, new Color(16, 185, 129, 130), 260, 120, new Color(99, 102, 241, 125)));
        graphics.fillRoundRect(18, 18, 284, 124, 28, 28);
        addNoiseTexture(graphics);
        graphics.dispose();
        return image;
    }

    private BufferedImage copy(BufferedImage source) {
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return image;
    }

    private int randomPieceX() {
        int max = WIDTH - PIECE_SIZE - PIECE_EDGE_PADDING;
        return MIN_PIECE_X + random.nextInt(max - MIN_PIECE_X + 1);
    }

    private int randomPieceY() {
        int max = HEIGHT - PIECE_SIZE - PIECE_EDGE_PADDING;
        return PIECE_EDGE_PADDING + random.nextInt(max - PIECE_EDGE_PADDING + 1);
    }

    private Shape puzzleShape(int x, int y) {
        Area area = new Area(new RoundRectangle2D.Double(x, y, PIECE_SIZE, PIECE_SIZE, 8, 8));
        int knob = 14;
        area.add(new Area(new Ellipse2D.Double(x + 19, y - knob / 2.0, knob, knob)));
        area.subtract(new Area(new Ellipse2D.Double(x + PIECE_SIZE - knob / 2.0, y + 19, knob, knob)));
        area.add(new Area(new Ellipse2D.Double(x + 19, y + PIECE_SIZE - knob / 2.0, knob, knob)));
        return area;
    }

    private String dataUrl(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Captcha image encoding failed", exception);
        }
    }

    private void enableQuality(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
