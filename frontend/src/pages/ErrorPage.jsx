import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { AlertTriangle, ArrowLeft } from 'lucide-react'

function ErrorPage() {
  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5 }}
        className="text-center max-w-md"
      >
        <div className="w-20 h-20 bg-terracotta-100 rounded-2xl flex items-center justify-center mx-auto mb-6">
          <AlertTriangle className="w-10 h-10 text-terracotta-500" />
        </div>
        <h1 className="font-display text-6xl font-bold text-sage-900 mb-2">404</h1>
        <p className="text-lg text-sage-600 mb-2">Page not found</p>
        <p className="text-sm text-sage-500 mb-8">
          The page you are looking for does not exist or has been moved.
        </p>
        <Link to="/" className="btn-primary inline-flex">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to home
        </Link>
      </motion.div>
    </div>
  )
}

export default ErrorPage
