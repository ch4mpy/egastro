
import { ref } from 'vue'

const bff = 'https://192.168.1.182:7080'

export interface LoginOptionDto {
    label: string,
    href: string
}

export class UserService {
  readonly current = ref(User.ANONYMOUS)
  private refreshIntervalId?: number

  constructor() {
    this.refresh()
  }

  async refresh(): Promise<User> {
    if(this.refreshIntervalId) {
        clearInterval(this.refreshIntervalId)
    }
    const response = await fetch(`${bff}/bff/v1/users/me`)
    const body = await response.json()
    this.current.value = body.name ? new User(body.name, body.realm, body.roles || [], body.manages || [], body.worksFor || []) : User.ANONYMOUS
    if (body.name) {
      const now = Date.now()
      const delay = (1000 * body.exp - now) * 0.8
      if (delay > 2000) {
        this.refreshIntervalId = setInterval(this.refresh, delay)
      }
    }
    return this.current.value
  }

  login(loginUri: string) {
    window.location.href = loginUri
  }

  async logout(xsrfToken: string) {
    const response = await fetch(`${bff}/logout`, { method: 'POST', headers: { 'X-XSRF-TOKEN': xsrfToken } })
    const location = response.headers.get('Location');
    if (location) {
        window.location.href = location;
    }
  }

  async loginOptions(): Promise<Array<LoginOptionDto>>{
    const response = await fetch(`${bff}/login-options`)
    return await response.json()
  }
}

export class User {
  static readonly ANONYMOUS = new User('', '', [], [], [])

  constructor(
    readonly name: string,
    readonly realm: string,
    readonly roles: string[],
    readonly manages: number[],
    readonly worksFor: number[]
  ) {}

  get isAuthenticated(): boolean {
    return !!this.name
  }

  hasAnyRole(...roles: string[]): boolean {
    for (const r of roles) {
      if (this.roles.includes(r)) {
        return true
      }
    }
    return false
  }
}
