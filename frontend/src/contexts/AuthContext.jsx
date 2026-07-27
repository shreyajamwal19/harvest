import { createContext, useContext, useState, useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    const token = localStorage.getItem('harvest_token')
    const storedUser = localStorage.getItem('harvest_user')
    if (token && storedUser) {
      try {
        setUser(JSON.parse(storedUser))
      } catch {
        localStorage.removeItem('harvest_token')
        localStorage.removeItem('harvest_user')
      }
    }
    setLoading(false)
  }, [])

  const login = useCallback(async (email, password) => {
    const response = await api.post('/auth/login', { email, password })
    const { token, user: userData } = response.data
    localStorage.setItem('harvest_token', token)
    localStorage.setItem('harvest_user', JSON.stringify(userData))
    setUser(userData)
    navigate('/')
    return userData
  }, [navigate])

  const signup = useCallback(async (name, email, password) => {
    const response = await api.post('/auth/signup', { name, email, password })
    const { token, user: userData } = response.data
    localStorage.setItem('harvest_token', token)
    localStorage.setItem('harvest_user', JSON.stringify(userData))
    setUser(userData)
    navigate('/')
    return userData
  }, [navigate])

  const logout = useCallback(() => {
    localStorage.removeItem('harvest_token')
    localStorage.removeItem('harvest_user')
    setUser(null)
    navigate('/')
  }, [navigate])

  const value = {
    user,
    isAuthenticated: !!user,
    loading,
    login,
    signup,
    logout,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
