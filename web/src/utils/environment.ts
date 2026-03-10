/**
 * @file environment.ts
 * @description 环境检测工具，用于判断当前运行环境（本地开发、局域网、生产环境）喵。
 */

export type EnvironmentType = 'local-dev' | 'lan' | 'production' | 'unknown'

export interface EnvironmentInfo {
  type: EnvironmentType
  hostname: string
  port: string
  protocol: string
  isLocalDevelopment: boolean
  isLocalNetwork: boolean
  isProduction: boolean
  isDevPort: boolean
  autoAdminEnabled: boolean
}

class EnvironmentDetector {
  private cachedInfo: EnvironmentInfo | null = null

  /**
   * 获取当前环境信息喵。
   */
  getEnvironment(): EnvironmentInfo {
    if (this.cachedInfo) {
      return this.cachedInfo
    }

    const { hostname, port, protocol } = window.location
    const isDevPort = port === '5173' // Vite开发服务器端口

    // 检测是否为本地开发环境
    const isLocalhost = hostname === 'localhost' || hostname === '127.0.0.1'
    const isPrivateIP = this.isPrivateIP(hostname)

    // 判断环境类型
    let type: EnvironmentType = 'unknown'
    let autoAdminEnabled = false

    if (isLocalhost && isDevPort) {
      // 本地开发环境（通过开发服务器访问）
      type = 'local-dev'
      autoAdminEnabled = true // 开发端口自动启用管理员权限
    } else if (isLocalhost || isPrivateIP) {
      // 局域网环境（本地主机或私有IP）
      type = 'lan'
      autoAdminEnabled = false // 局域网需要正常登录
    } else {
      // 生产环境（公网域名）
      type = 'production'
      autoAdminEnabled = false
    }

    const info: EnvironmentInfo = {
      type,
      hostname,
      port,
      protocol,
      isLocalDevelopment: type === 'local-dev',
      isLocalNetwork: type === 'local-dev' || type === 'lan',
      isProduction: type === 'production',
      isDevPort,
      autoAdminEnabled,
    }

    this.cachedInfo = info
    return info
  }

  /**
   * 检测是否为私有IP地址喵。
   */
  private isPrivateIP(hostname: string): boolean {
    // 私有IP地址范围：
    // 192.168.x.x, 10.x.x.x, 172.16.x.x-172.31.x.x
    const ipPatterns = [
      /^192\.168\.\d+\.\d+$/,
      /^10\.\d+\.\d+\.\d+$/,
      /^172\.(1[6-9]|2[0-9]|3[0-1])\.\d+\.\d+$/,
    ]
    return ipPatterns.some(pattern => pattern.test(hostname))
  }

  /**
   * 获取当前环境的显示名称喵。
   */
  getEnvironmentDisplayName(): string {
    const env = this.getEnvironment()
    switch (env.type) {
      case 'local-dev':
        return '本地开发环境'
      case 'lan':
        return '局域网环境'
      case 'production':
        return '生产环境'
      default:
        return '未知环境'
    }
  }

  /**
   * 检查是否应该自动启用管理员权限喵。
   * 注意：这仅影响前端UI显示，真正的权限验证由后端API控制喵。
   */
  shouldAutoEnableAdmin(): boolean {
    const env = this.getEnvironment()

    // 开发端口（5173）自动启用管理员UI权限
    // 这允许开发人员在本地测试管理员功能，无需每次登录喵
    if (env.autoAdminEnabled) {
      console.log('[环境检测] 开发环境，自动启用管理员UI权限喵')
      return true
    }

    console.log(`[环境检测] ${env.type}环境，需要正常登录获取权限喵`)
    return false
  }

  /**
   * 获取环境标签的CSS类名喵。
   */
  getEnvironmentBadgeClass(): string {
    const env = this.getEnvironment()
    switch (env.type) {
      case 'local-dev':
        return 'env-badge-local-dev'
      case 'lan':
        return 'env-badge-lan'
      case 'production':
        return 'env-badge-production'
      default:
        return 'env-badge-unknown'
    }
  }

  /**
   * 获取环境标签的显示文本喵。
   */
  getEnvironmentBadgeText(): string {
    const env = this.getEnvironment()
    switch (env.type) {
      case 'local-dev':
        return 'DEV'
      case 'lan':
        return 'LAN'
      case 'production':
        return 'PROD'
      default:
        return 'UNK'
    }
  }
}

export const environment = new EnvironmentDetector()

/**
 * 组合式函数：在Vue组件中使用环境信息喵。
 */
export function useEnvironment() {
  const env = environment.getEnvironment()

  return {
    env,
    isLocalDev: env.isLocalDevelopment,
    isLocalNetwork: env.isLocalNetwork,
    isProduction: env.isProduction,
    shouldAutoAdmin: env.autoAdminEnabled,
    displayName: environment.getEnvironmentDisplayName(),
    badgeClass: environment.getEnvironmentBadgeClass(),
    badgeText: environment.getEnvironmentBadgeText(),
  }
}

export default environment