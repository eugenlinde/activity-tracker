export const LOGIN_ROUTE = '/login'
export const DASHBOARD_ROUTE = '/'
export const ACTIVITIES_ROUTE = '/activities'
export const SHARES_ROUTE = '/shares'
export const IMPORT_ROUTE = '/import'
export const SHARED_ROUTE = '/shared/:ownerId'
export const ACTIVITY_ROUTE = '/activities/:id'
export const FALLBACK_ROUTE = '*'

export function activityPath(id: number): string {
  return `/activities/${id}`
}

export const ROUTE_PATTERNS: readonly string[] = [
  LOGIN_ROUTE,
  DASHBOARD_ROUTE,
  ACTIVITIES_ROUTE,
  SHARES_ROUTE,
  IMPORT_ROUTE,
  SHARED_ROUTE,
  ACTIVITY_ROUTE,
  FALLBACK_ROUTE,
]
