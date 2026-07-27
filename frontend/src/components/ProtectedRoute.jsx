import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Loading from './Loading'

/**
 * Guards nested routes behind authentication. While the initial session
 * check (GET /user/me) is in flight we show a loading state rather than
 * flashing the login page. Once resolved, unauthenticated visitors are sent
 * to /login with the page they wanted attached, so we can send them back
 * after they log in.
 */
function ProtectedRoute() {
  const { isAuthenticated, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <Loading />
  }

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        state={{ from: location, message: 'Please log in to continue.' }}
        replace
      />
    )
  }

  return <Outlet />
}

export default ProtectedRoute
