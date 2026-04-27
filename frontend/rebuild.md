# AI 智能客服平台前端设计文档 (SDD 2.1 - Spatial & Fluid)

## 1. 概述

### 1.1 项目背景

本项目是一个基于 RAG（检索增强生成）技术的智能客服平台前端，旨在为用户提供智能问答、业务咨询和服务协同入口。通过本次重构，将彻底摆脱传统后台管理系统的刻板印象，打造具有前瞻性和品牌识别度的现代 AI 工作空间。

### 1.2 设计目标

以 Spatial & Fluid（空间与流体）为核心视觉理念，构建一个不仅好用，且充满生命力的界面：

- **流体空间（Fluid Space）**：采用有机的流体网格渐变（Mesh Gradient）作为底层呼吸背景，打破传统区块的生硬感。
- **空间质感（Spatial Depth）**：升级毛玻璃效果，引入边缘折射（Inner Highlight）与多层环境光阴影，构建清晰的 Z 轴空间层级。
- **物理动效（Spring Physics）**：采用弹性缓动曲线，配合微交互（如聚光灯跟随、磁性吸附），但所有持续动画必须支持 `prefers-reduced-motion`。
- **沉浸阅读（Immersive Reading）**：优化大段文本的排版，配合悬浮式组件布局，最大化内容展示区。
- **可访问性优先（Accessibility First）**：文本、边框、焦点、动画和键盘导航必须先满足 WCAG 2.1 AA，再做视觉增强。

### 1.3 技术选型

保持原有 Vue 3 + Pinia 架构，迁移 UI 层到 Naive UI，并新增现代 CSS 方案。

| 类别 | 技术 | 说明 |
| --- | --- | --- |
| 框架 | Vue 3 (Composition API) | 保持原有 |
| UI 框架 | Naive UI | 替代 Element Plus，通过 `NConfigProvider` 和 `themeOverrides` 注入主题变量 |
| 样式增强 | CSS Variables + Tailwind 理念 | 引入 Spotlight、Mesh Gradient 等高级 CSS 效果 |
| 图标 | @vicons/fluent 或 Phosphor Icons | 替代 Ionicons，线条更圆润、现代 |

## 2. 设计系统

### 2.1 Token 分层与全局变量

实际落地时不应把色彩、排版、阴影和动画分散在多个互不关联的 `:root` 中。建议在 `src/styles/global.css` 中集中维护全局 token，并按 Primitive / Semantic / Component 三层组织。

```text
src/styles/
├─ global.css          # 引入 tokens、基础 reset、全局可访问性规则
├─ tokens.css          # Primitive / Semantic token
├─ naive-theme.ts      # Naive UI themeOverrides 映射
└─ components.css      # 轻量组件级补充样式，避免深层 :deep()
```

```css
:root {
  /* Primitive: light palette */
  --color-white: #ffffff;
  --color-gray-50: #f8fafc;
  --color-gray-100: #eef2f7;
  --color-gray-600: #4b5563;
  --color-gray-700: #374151;
  --color-gray-900: #111827;
  --color-blue-700: #0066cc;
  --color-blue-800: #0057b8;
  --color-blue-900: #003e7e;
  --color-cyan-500: #159bd3;
  --color-purple-500: #9b4dcc;

  /* Semantic: surface */
  --bg-app: var(--color-gray-50);
  --bg-surface: var(--color-white);
  --bg-surface-muted: var(--color-gray-100);
  --glass-surface: rgba(255, 255, 255, 0.82);
  --glass-surface-hover: rgba(255, 255, 255, 0.92);
  --glass-surface-solid: #ffffff;

  /* Semantic: text */
  --text-primary: var(--color-gray-900);
  --text-secondary: var(--color-gray-700);
  --text-muted: var(--color-gray-600);
  --text-on-accent: #ffffff;

  /* Semantic: border and focus */
  --border-subtle: rgba(17, 24, 39, 0.12);
  --border-contrast: #767b84;
  --glass-border: var(--border-contrast);
  --focus-ring: var(--color-blue-900);
  --focus-ring-offset: var(--bg-surface);

  /* Semantic: brand */
  --accent-primary: var(--color-blue-700);
  --accent-primary-hover: var(--color-blue-800);
  --accent-primary-muted: rgba(0, 102, 204, 0.14);
  --accent-cyan: var(--color-cyan-500);
  --accent-purple: var(--color-purple-500);

  /* Mesh background */
  --mesh-color-1: rgba(90, 200, 250, 0.12);
  --mesh-color-2: rgba(0, 102, 204, 0.1);
  --mesh-color-3: rgba(155, 77, 204, 0.1);

  /* Typography */
  --font-sans: "Plus Jakarta Sans", "Inter", "MiSans", "PingFang SC", -apple-system, sans-serif;
  --font-mono: "JetBrains Mono", "SF Mono", ui-monospace, monospace;
  --leading-tight: 1.2;
  --leading-base: 1.6;
  --leading-relaxed: 1.8;
  --tracking-normal: 0em;

  /* Depth */
  --shadow-sm:
    0 1px 2px rgba(0, 0, 0, 0.03),
    0 4px 12px rgba(0, 0, 0, 0.04);
  --shadow-md:
    0 4px 6px rgba(0, 0, 0, 0.04),
    0 12px 32px rgba(0, 0, 0, 0.08);
  --shadow-lg:
    0 12px 24px rgba(0, 0, 0, 0.06),
    0 32px 64px rgba(0, 0, 0, 0.1),
    0 0 0 1px rgba(0, 0, 0, 0.03);
  --glass-highlight:
    inset 0 1px 1px rgba(255, 255, 255, 0.85),
    inset 0 0 0 1px rgba(255, 255, 255, 0.35);

  /* Motion */
  --ease-spring: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-out-quint: cubic-bezier(0.22, 1, 0.36, 1);
  --duration-fast: 150ms;
  --duration-base: 300ms;
  --duration-spring: 600ms;

  /* Performance budget */
  --glass-blur-card: 18px;
  --glass-blur-floating: 20px;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-white: #111827;
    --color-gray-50: #0f172a;
    --color-gray-100: #1e293b;
    --color-gray-600: #cbd5e1;
    --color-gray-700: #e2e8f0;
    --color-gray-900: #f8fafc;
    --color-blue-700: #4da3ff;
    --color-blue-800: #78b7ff;
    --color-blue-900: #9cc9ff;

    --bg-app: #0f172a;
    --bg-surface: #111827;
    --bg-surface-muted: #1e293b;
    --glass-surface: rgba(17, 24, 39, 0.86);
    --glass-surface-hover: rgba(30, 41, 59, 0.92);
    --glass-surface-solid: #111827;

    --text-primary: #f8fafc;
    --text-secondary: #e2e8f0;
    --text-muted: #cbd5e1;
    --text-on-accent: #07111f;

    --border-subtle: rgba(226, 232, 240, 0.18);
    --border-contrast: #94a3b8;
    --glass-border: var(--border-contrast);
    --focus-ring: #9cc9ff;
    --focus-ring-offset: var(--bg-surface);

    --mesh-color-1: rgba(77, 163, 255, 0.14);
    --mesh-color-2: rgba(21, 155, 211, 0.12);
    --mesh-color-3: rgba(155, 77, 204, 0.14);
  }
}
```

### 2.2 可访问性基线

#### 2.2.1 对比度结论

所有交互 UI 边界和文本颜色必须按最差背景组合验证，而不是只在静态白底上目测。

| 组合 | 用途 | 目标 | 结论 |
| --- | --- | --- | --- |
| `--text-primary` / `--glass-surface-solid` | 主正文、表单内容 | WCAG AA 正文 4.5:1 | 亮色约 16:1，暗色约 17:1，通过 |
| `--text-secondary` / `--glass-surface-solid` | 次级说明、辅助标签 | WCAG AA 正文 4.5:1 | 亮色约 10:1，暗色约 13:1，通过 |
| `--accent-primary` / `--text-on-accent` | 主按钮文本 | WCAG AA 正文 4.5:1 | 亮色约 5.6:1，通过；暗色使用深色文字，同样需通过 |
| `--glass-border` / `--bg-surface` | 输入框、卡片、按钮边界 | WCAG 2.1 非文本 UI 3:1 | 亮色约 4.2:1，暗色约 6:1，通过，并为 mesh 背景留出余量 |
| `--focus-ring` / `--bg-surface` | 键盘焦点环 | WCAG 2.1 非文本 UI 3:1 | 亮色和暗色均高于 3:1，通过 |

`--glass-surface` 不能单独承担可读性。凡是承载正文、表单、按钮或导航文本的玻璃态容器，必须同时满足：

- 背景透明度不低于 `0.82`，避免渐变背景干扰文字。
- 文本只使用 `--text-primary`、`--text-secondary`、`--text-muted` 或 `--text-on-accent`。
- 对图表、标签、禁用态等低对比元素，必须补充图标、形状或文本，不只依赖颜色。

#### 2.2.2 焦点样式

所有可交互元素必须有可见的 `:focus-visible`。焦点环不得被 `overflow: hidden` 裁切；如果元素必须裁切伪元素，需要把焦点样式放在父级或使用 `outline-offset`。

```css
:where(a, button, input, textarea, select, [tabindex]:not([tabindex="-1"])):focus-visible,
.n-button:focus-visible,
.n-input:focus-within,
.kb-card[role="button"]:focus-visible,
.conversation-item:focus-visible {
  outline: 3px solid var(--focus-ring);
  outline-offset: 3px;
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--focus-ring) 24%, transparent);
}
```

#### 2.2.3 减少动态效果

`meshBreathe`、`shimmerText` 和所有持续循环动画必须遵守 `prefers-reduced-motion`。用户选择减少动态效果时，保留最终静态视觉，不播放循环动画。

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto;
    animation-duration: 0.01ms;
    animation-iteration-count: 1;
    transition-duration: 0.01ms;
  }

  .login-page,
  .login-page::before,
  .ai-generating-text {
    animation: none;
  }

  .ai-generating-text {
    background: none;
    -webkit-text-fill-color: currentColor;
  }
}
```

### 2.3 现代排版系统

```css
body {
  font-family: var(--font-sans);
  color: var(--text-primary);
  background: var(--bg-app);
  line-height: var(--leading-base);
  letter-spacing: var(--tracking-normal);
}

.ai-answer {
  line-height: var(--leading-relaxed);
  color: var(--text-primary);
}

.meta-text {
  color: var(--text-muted);
}
```

### 2.4 动画系统

```css
@keyframes spatialFadeIn {
  0% {
    opacity: 0;
    transform: translateY(16px) scale(0.98);
    filter: blur(4px);
  }

  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
    filter: blur(0);
  }
}

@keyframes meshBreathe {
  0%, 100% {
    transform: scale(1) translate(0, 0);
  }

  33% {
    transform: scale(1.03) translate(1.5%, -1.5%);
  }

  66% {
    transform: scale(0.99) translate(-1%, 2%);
  }
}
```

## 3. 核心页面重构

### 3.1 登录页 (LoginView) - 沉浸式流体体验

#### 3.1.1 布局结构

```text
┌──────────────────────────────────────────────────────────────────┐
│  [动态流体网格渐变背景 Mesh Gradient]                              │
│                                                                  │
│       ┌──────────────────────────────────────────────────┐       │
│       │                                                  │       │
│       │   [品牌 Logo]  ✨ 智能客服工作站                 │       │
│       │                                                  │       │
│       │   ┌──────────────────────────────────────────┐   │       │
│       │   │                                          │   │       │
│       │   │  欢迎回来                                │   │       │
│       │   │  请登录以继续探索您的 AI 知识库           │   │       │
│       │   │                                          │   │       │
│       │   │  [ 账户名输入框 (带聚焦光晕) ]            │   │       │
│       │   │  [ 密码输入框 ]                          │   │       │
│       │   │                                          │   │       │
│       │   │  [登录按钮 (悬浮发光效果)]               │   │       │
│       │   │                                          │   │       │
│       │   └──────────────────────────────────────────┘   │       │
│       │                                                  │       │
│       └──────────────────────────────────────────────────┘       │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### 3.1.2 关键 CSS 实现

```css
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background-color: var(--bg-app);
}

.login-page::before {
  content: "";
  position: absolute;
  inset: -6%;
  background-image:
    radial-gradient(at 0% 0%, var(--mesh-color-1) 0px, transparent 50%),
    radial-gradient(at 100% 0%, var(--mesh-color-2) 0px, transparent 50%),
    radial-gradient(at 100% 100%, var(--mesh-color-3) 0px, transparent 50%);
  animation: meshBreathe 15s ease-in-out infinite alternate;
  will-change: transform;
}

.login-card {
  position: relative;
  z-index: 1;
  width: min(100%, 440px);
  padding: 48px;
  color: var(--text-primary);
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-card));
  -webkit-backdrop-filter: blur(var(--glass-blur-card));
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  box-shadow: var(--shadow-lg), var(--glass-highlight);
  animation: spatialFadeIn var(--duration-spring) var(--ease-spring) both;
}

.login-card:hover {
  background: var(--glass-surface-hover);
}

.n-input {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.72);
  transition:
    background-color var(--duration-base) var(--ease-out-quint),
    box-shadow var(--duration-base) var(--ease-out-quint),
    transform var(--duration-base) var(--ease-out-quint);
}

.n-input:focus-within {
  background: var(--bg-surface);
  transform: translateY(-1px);
}
```

### 3.2 聊天页 (ChatView) - 悬浮舱式布局

#### 3.2.1 布局结构

```text
┌──────────────────────────────────────────────────────────────────┐
│  ┌──────────────┐  ┌────────────────────────────────────────┐  │
│  │ 🔍 搜索会话   │  │ 智能客服 · 知识库 [设计系统] ▼           │  │
│  │ ＋ 新建对话   │  ├────────────────────────────────────────┤  │
│  ├──────────────┤  │                                        │  │
│  │ [今天]       │  │  ┌────────────────────────────────┐    │  │
│  │ 💬 会话 1    │  │  │ 🤖 气泡 (带生成时的流光边框)    │    │  │
│  │ 💬 会话 2    │  │  │ 根据文档，推荐使用流体渐变...   │    │  │
│  │              │  │  │ [引用: SDD.md]                 │    │  │
│  │ [昨天]       │  │  └────────────────────────────────┘    │  │
│  │ 💬 会话 3    │  │                                        │  │
│  │              │  │              ┌────────────────────────┐  │  │
│  │              │  │              │ 我的提问气泡           │  │  │
│  │              │  │              └────────────────────────┘  │  │
│  │              │  │                                        │  │
│  │              │  │       ┌────────────────────────┐       │  │
│  │              │  │       │ 📎 输入您的问题...  [➤]│       │  │
│  │              │  │       └────────────────────────┘       │  │
│  └──────────────┘  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

#### 3.2.2 关键 CSS 实现

```css
.chat-workspace {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  margin: 16px 16px 16px 0;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: 24px;
  box-shadow: var(--shadow-sm);
}

.message-content {
  padding: 16px 20px;
  border-radius: 20px;
  font-size: 15px;
  line-height: var(--leading-base);
  letter-spacing: var(--tracking-normal);
}

.message-wrapper.ai .message-content {
  color: var(--text-primary);
  background: var(--bg-app);
  border: 1px solid var(--glass-border);
  border-top-left-radius: 4px;
}

.message-wrapper.user .message-content {
  color: var(--text-on-accent);
  background: var(--accent-primary);
  border-top-right-radius: 4px;
  box-shadow: 0 8px 16px var(--accent-primary-muted);
}

.floating-input-container {
  position: absolute;
  bottom: 32px;
  left: 50%;
  width: min(80%, 760px);
  padding: 8px 16px;
  transform: translateX(-50%);
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-floating));
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  box-shadow: var(--shadow-lg), var(--glass-highlight);
  transition:
    transform var(--duration-spring) var(--ease-spring),
    box-shadow var(--duration-spring) var(--ease-spring),
    background-color var(--duration-base) var(--ease-out-quint);
}

.floating-input-container:focus-within {
  transform: translateX(-50%) translateY(-2px);
  background: var(--glass-surface-hover);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08), var(--glass-highlight);
}
```

### 3.3 知识库页 (KnowledgeBaseView) - Spotlight 聚光灯卡片

在知识库卡片上引入现代 Web 常见的聚光灯（Spotlight）悬停效果，提升探索感。Spotlight 不能只依赖 hover；键盘聚焦时也应展示相同的强调反馈。

#### 3.3.1 关键 CSS 实现

```css
.kb-card {
  position: relative;
  padding: 24px;
  overflow: hidden;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--glass-border);
  border-radius: 20px;
  transition:
    transform var(--duration-spring) var(--ease-spring),
    box-shadow var(--duration-spring) var(--ease-spring),
    border-color var(--duration-base) var(--ease-out-quint),
    background-color var(--duration-base) var(--ease-out-quint);
}

.kb-card::before {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(
    800px circle at var(--spotlight-x, 50%) var(--spotlight-y, 50%),
    rgba(0, 102, 204, 0.1),
    transparent 40%
  );
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--duration-base) var(--ease-out-quint);
}

.kb-card:hover,
.kb-card[role="button"]:focus-visible {
  transform: translateY(-4px) scale(1.01);
  background: var(--glass-surface-hover);
  border-color: var(--accent-primary);
  box-shadow: var(--shadow-md);
}

.kb-card:hover::before,
.kb-card[role="button"]:focus-visible::before {
  opacity: 1;
}

.kb-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  font-size: 24px;
  background: linear-gradient(135deg, var(--bg-app), var(--bg-surface));
  border-radius: 14px;
  box-shadow: var(--shadow-sm), inset 0 1px 1px rgba(255, 255, 255, 0.7);
}
```

#### 3.3.2 `useSpotlight` 组合式函数

Spotlight 的 `--spotlight-x` 和 `--spotlight-y` 必须由 Vue composable 管理事件绑定和解绑，避免组件卸载后残留监听器。

```ts
import { onBeforeUnmount, onMounted, ref } from "vue";

export function useSpotlight<T extends HTMLElement>() {
  const target = ref<T | null>(null);
  let frame = 0;

  const update = (event: PointerEvent) => {
    const element = target.value;
    if (!element) return;

    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      const rect = element.getBoundingClientRect();
      const x = event.clientX - rect.left;
      const y = event.clientY - rect.top;
      element.style.setProperty("--spotlight-x", `${x}px`);
      element.style.setProperty("--spotlight-y", `${y}px`);
    });
  };

  onMounted(() => {
    target.value?.addEventListener("pointermove", update);
  });

  onBeforeUnmount(() => {
    cancelAnimationFrame(frame);
    target.value?.removeEventListener("pointermove", update);
  });

  return { target };
}
```

```vue
<template>
  <article ref="target" class="kb-card" role="button" tabindex="0">
    <!-- card content -->
  </article>
</template>
```

## 4. 现代交互细节规范

### 4.1 按钮交互 (Magnetic & Glow)

抛弃生硬的颜色切换，改为带有轻微缩放和光晕的现代按钮。主色不要通过 `!important` 覆盖，应由 Naive UI `themeOverrides` 从源头注入。

```css
.n-button--primary-type {
  position: relative;
  overflow: hidden;
  font-weight: 600;
  letter-spacing: 0.02em;
  border-radius: 12px;
  transition:
    transform var(--duration-spring) var(--ease-spring),
    box-shadow var(--duration-spring) var(--ease-spring),
    border-color var(--duration-base) var(--ease-out-quint),
    background-color var(--duration-base) var(--ease-out-quint);
}

.n-button--primary-type::after {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 50%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.24), transparent);
}

.n-button--primary-type:hover {
  transform: scale(1.03);
  box-shadow: 0 8px 20px var(--accent-primary-muted);
}

.n-button--primary-type:hover::after {
  left: 200%;
  transition: left 0.8s ease-in-out;
}
```

### 4.2 AI 生成状态动画 (Shimmer Effect)

当 AI 正在思考或生成文本时，使用骨架屏的微光扫过效果，而不是简单的 Loading 圈。该效果必须有 `aria-live` 文案，并在减少动态效果时退化为静态文本。

```vue
<template>
  <span class="sr-only" aria-live="polite">
    {{ liveMessage }}
  </span>

  <span v-if="isGenerating" class="ai-generating-text" aria-hidden="true">
    AI 正在生成回答...
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  isGenerating: boolean;
  answer: string;
}>();

const liveMessage = computed(() => {
  if (props.isGenerating) return "AI 正在生成回答...";
  if (props.answer) return "AI 回答已生成。";
  return "";
});
</script>
```

生成结束时必须把 `isGenerating` 置为 `false`，并将 `aria-live` 区域更新为完成提示或清空。不要让屏幕阅读器持续保留“AI 正在生成回答...”这一状态；最终回答正文应作为普通消息内容渲染，避免重复播报。

```css
.ai-generating-text {
  color: var(--text-secondary);
  background: linear-gradient(90deg, var(--text-primary) 25%, var(--text-muted) 50%, var(--text-primary) 75%);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: shimmerText 2s infinite linear;
}

@keyframes shimmerText {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}
```

## 5. 响应式与移动端策略

管理侧主体验优先桌面和平板，但不能留空小屏行为。

| 断点 | 策略 |
| --- | --- |
| `>= 1024px` | 完整桌面布局：侧边栏 + 聊天工作区 + 悬浮输入舱 |
| `768px - 1023px` | 平板布局：侧边栏默认收起为抽屉，顶部保留知识库选择器 |
| `< 768px` | 小屏基础支持：登录、查看会话、发送消息可用；复杂知识库管理入口显示为简化列表或提示到桌面端处理 |

平板布局使用 Naive UI `n-drawer` 承载会话侧边栏。顶部工具栏提供一个明确的菜单按钮，按钮文本应通过 `aria-label` 暴露给屏幕阅读器；抽屉关闭时焦点回到触发按钮。

```vue
<template>
  <button
    ref="drawerTrigger"
    class="sidebar-toggle"
    type="button"
    aria-label="打开会话列表"
    @click="drawerVisible = true"
  >
    <MenuIcon aria-hidden="true" />
  </button>

  <n-drawer
    v-model:show="drawerVisible"
    placement="left"
    :width="320"
    @after-leave="drawerTrigger?.focus()"
  >
    <n-drawer-content title="会话列表" closable>
      <ChatSidebar />
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { ref } from "vue";

const drawerVisible = ref(false);
const drawerTrigger = ref<HTMLButtonElement | null>(null);
</script>
```

```css
@media (min-width: 768px) and (max-width: 1023px) {
  .chat-sidebar {
    display: none;
  }

  .sidebar-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border: 1px solid var(--glass-border);
    border-radius: 10px;
    color: var(--text-primary);
    background: var(--bg-surface);
  }
}
```

```css
@media (max-width: 767px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    width: 100%;
    padding: 28px 20px;
    border-radius: 18px;
  }

  .chat-sidebar {
    display: none;
  }

  .chat-workspace {
    margin: 0;
    border-radius: 0;
  }

  .floating-input-container {
    position: fixed;
    bottom: max(12px, env(safe-area-inset-bottom));
    width: calc(100vw - 24px);
    max-width: none;
    border-radius: 18px;
  }
}
```

## 6. Naive UI 主题覆盖

Naive UI 主题必须通过 `NConfigProvider` 的 `theme-overrides` 注入。当前前端依赖中尚未安装 `naive-ui`，迁移时应安装后以 `GlobalThemeOverrides` 类型校验字段。

### 6.1 映射表

| 本文 CSS 变量 | Naive UI `themeOverrides` 字段 | 用途 |
| --- | --- | --- |
| `--accent-primary` | `common.primaryColor` | 主按钮、选中态、主要交互色 |
| `--accent-primary-hover` | `common.primaryColorHover` | hover 主色 |
| `--accent-primary` 的按下态派生值 | `common.primaryColorPressed` | pressed 主色 |
| `--accent-primary-muted` | `common.primaryColorSuppl` | 辅助主色背景 |
| `--text-primary` | `common.textColor1` | 主文本 |
| `--text-secondary` | `common.textColor2` | 次级文本 |
| `--text-muted` | `common.textColor3` | 弱文本 |
| `--border-subtle` | `common.borderColor` | 默认边框 |
| `12px` | `common.borderRadius` | 默认圆角 |
| `--font-sans` | `common.fontFamily` | 全局字体 |
| `--font-mono` | `common.fontFamilyMono` | 等宽字体 |
| `--shadow-sm` | `common.boxShadow1` | 低层级阴影 |
| `--shadow-md` | `common.boxShadow2` | 中层级阴影 |
| `--shadow-lg` | `common.boxShadow3` | 高层级阴影 |
| `--ease-out-quint` | `common.cubicBezierEaseOut` | 常规离场或响应动画 |
| `--ease-spring` | `common.cubicBezierEaseInOut` | 主动效缓动 |

### 6.2 示例

```ts
import type { GlobalThemeOverrides } from "naive-ui";

export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: "var(--accent-primary)",
    primaryColorHover: "var(--accent-primary-hover)",
    primaryColorPressed: "#004a99",
    primaryColorSuppl: "var(--accent-primary-muted)",
    textColor1: "var(--text-primary)",
    textColor2: "var(--text-secondary)",
    textColor3: "var(--text-muted)",
    borderColor: "var(--border-subtle)",
    borderRadius: "12px",
    fontFamily: "var(--font-sans)",
    fontFamilyMono: "var(--font-mono)",
    boxShadow1: "var(--shadow-sm)",
    boxShadow2: "var(--shadow-md)",
    boxShadow3: "var(--shadow-lg)",
    cubicBezierEaseOut: "var(--ease-out-quint)",
    cubicBezierEaseInOut: "var(--ease-spring)"
  },
  Button: {
    borderRadiusMedium: "12px",
    fontWeight: "600"
  },
  Input: {
    borderRadius: "12px"
  },
  Card: {
    borderRadius: "20px"
  }
};
```

```vue
<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <router-view />
  </n-config-provider>
</template>
```

## 7. 工程化落地建议

- **A11y 验收优先**：每个页面必须验证键盘 Tab 顺序、`focus-visible`、文本对比度、非文本 UI 对比度和 `prefers-reduced-motion`。
- **CSS 变量分层**：`tokens.css` 统一维护 Primitive / Semantic token，组件样式只消费 Semantic token，不直接写硬编码颜色。
- **Naive UI 主题覆盖**：通过 `NConfigProvider` 注入主题变量，避免用 `!important` 或大量 `:deep()` 打补丁。
- **性能预算**：同一页面最多保留一个高模糊玻璃核心元素；模糊半径控制在 `16px - 20px`。背景动画独立在伪元素层，父容器必须 `overflow: hidden`。
- **显式 transition**：禁止 `transition: all`。只允许列出 `transform`、`opacity`、`box-shadow`、`border-color`、`background-color` 等确实需要动画的属性。
- **Spotlight 生命周期**：所有鼠标或指针监听器必须通过 composable 在 `onMounted` 绑定，在 `onBeforeUnmount` 清理。
- **暗色模式预留**：现在即维护 `@media (prefers-color-scheme: dark)` 覆盖结构，避免后期从亮色硬编码中返工。
