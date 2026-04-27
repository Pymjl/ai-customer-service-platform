import type { GlobalThemeOverrides } from 'naive-ui'

export const themeOverrides: GlobalThemeOverrides = {
  common: {
    // Naive UI 会用 seemly/rgba 对主题色做派生计算，颜色字段不能使用 CSS var。
    primaryColor: '#0066cc',
    primaryColorHover: '#0057b8',
    primaryColorPressed: '#004a99',
    primaryColorSuppl: 'rgba(0, 102, 204, 0.14)',
    textColor1: '#111827',
    textColor2: '#374151',
    textColor3: '#4b5563',
    borderColor: 'rgba(17, 24, 39, 0.12)',
    borderRadius: '12px',
    fontFamily: '"Plus Jakarta Sans", "Inter", "MiSans", "PingFang SC", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    fontFamilyMono: '"JetBrains Mono", "SF Mono", ui-monospace, Menlo, Consolas, monospace',
    boxShadow1: '0 1px 2px rgba(0, 0, 0, 0.03), 0 4px 12px rgba(0, 0, 0, 0.04)',
    boxShadow2: '0 4px 6px rgba(0, 0, 0, 0.04), 0 12px 32px rgba(0, 0, 0, 0.08)',
    boxShadow3: '0 12px 24px rgba(0, 0, 0, 0.06), 0 32px 64px rgba(0, 0, 0, 0.1), 0 0 0 1px rgba(0, 0, 0, 0.03)',
    cubicBezierEaseOut: 'cubic-bezier(0.22, 1, 0.36, 1)',
    cubicBezierEaseInOut: 'cubic-bezier(0.16, 1, 0.3, 1)'
  },
  Button: {
    borderRadiusMedium: '12px',
    fontWeight: '650'
  },
  Input: {
    borderRadius: '12px'
  },
  Card: {
    borderRadius: '20px'
  },
  Tag: {
    borderRadius: '999px'
  }
}
