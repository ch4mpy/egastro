import { ref } from 'vue'

const backend = 'https://192.168.1.182:7080'

export interface LoginOptionDto {
  label: string
  href: string
}

export class UserService {
  readonly current = ref(User.ANONYMOUS)
  private refreshIntervalId?: number

  constructor() {
    this.refresh()
  }

  async refresh(): Promise<User> {
    if (this.refreshIntervalId) {
      clearInterval(this.refreshIntervalId)
    }
    const response = await fetch(`${backend}/bff/v1/users/me`)
    const body = await response.json()
    this.current.value = body.user?.subject
      ? new User(
          body.user.username,
          body.user.subject,
          body.user.email || '',
          body.user.realm || '',
          body.user.roles || []
        )
      : User.ANONYMOUS
    if (body.user?.subject) {
      const now = Date.now()
      const delay = (1000 * body.exp - now) * 0.8
      if (delay > 2000) {
        this.refreshIntervalId = setInterval(this.refresh, delay)
      }
    }
    return this.current.value
  }

  login(loginUri: string, isSameTab: boolean) {
    if (isSameTab) {
      window.location.href = loginUri
    } else {
      window.open(
        loginUri,
        'Login',
        `toolbar=no, location=no, directories=no, status=no, menubar=no, scrollbars=no, resizable=no, width=800, height=600`
      )
    }
  }

  async logout(xsrfToken: string) {
    const response = await fetch(`${backend}/logout`, {
      method: 'POST',
      headers: {
        'X-XSRF-TOKEN': xsrfToken,
        'X-POST-LOGOUT-SUCCESS-URI': `${backend}/admin-console`
      }
    })
    const location = response.headers.get('Location')
    if (location) {
      window.location.href = location
    }
  }

  async loginOptions(): Promise<Array<LoginOptionDto>> {
    const response = await fetch(`${backend}/login-options`)
    return await response.json()
  }
}

export class User {
  static readonly ANONYMOUS = new User('', '', '', '', [])

  constructor(
    readonly username: string,
    readonly subject: string,
    readonly email: string,
    readonly realm: string,
    readonly roles: string[]
  ) {}

  get isAuthenticated(): boolean {
    return !!this.subject
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
