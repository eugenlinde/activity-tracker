import { theme, type ThemeConfig } from 'antd'

export const palette = {
  ground: '#15181D',
  card: '#1B1F25',
  input: '#181C22',
  raised: '#22272E',
  border: '#2E343D',
  borderSoft: '#22272E',
  text: '#E6E8EB',
  muted: '#8E97A3',
  sage: '#A8B9A3',
  sageHover: '#B8C8B3',
  blue: '#8DAACB',
  danger: '#C58F94',
  sand: '#C9B08A',
}

export const appTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    fontFamily: "'Manrope Variable', system-ui, sans-serif",
    fontSize: 15,
    colorPrimary: palette.sage,
    colorLink: palette.sage,
    colorError: palette.danger,
    colorBgBase: palette.ground,
    colorBgLayout: palette.ground,
    colorBgContainer: palette.card,
    colorBgElevated: palette.card,
    colorTextBase: palette.text,
    colorText: palette.text,
    colorTextSecondary: palette.muted,
    colorTextDescription: palette.muted,
    colorTextPlaceholder: '#5E6773',
    colorBorder: palette.border,
    colorBorderSecondary: palette.borderSoft,
    borderRadius: 8,
    borderRadiusLG: 12,
    controlHeight: 40,
  },
  components: {
    Button: {
      primaryColor: palette.ground,
      defaultBg: palette.raised,
      defaultBorderColor: palette.border,
      fontWeight: 500,
    },
    Input: {
      colorBgContainer: palette.input,
    },
    Select: {
      colorBgContainer: palette.input,
    },
    Menu: {
      itemBg: 'transparent',
      itemColor: palette.muted,
      itemHoverColor: palette.text,
      itemHoverBg: palette.raised,
      itemSelectedBg: palette.raised,
      itemSelectedColor: palette.text,
      itemHeight: 44,
      itemBorderRadius: 8,
      activeBarBorderWidth: 0,
    },
    Modal: {
      contentBg: palette.card,
      headerBg: palette.card,
    },
    Tag: {
      defaultBg: palette.raised,
      defaultColor: palette.text,
    },
  },
}
