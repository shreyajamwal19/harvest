import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'

function ErrorPage() {
  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4">
      <div className="text-center max-w-md">
        <p className="font-display text-7xl font-semibold text-ink-800/15 mb-2">404</p>
        <h1 className="font-display text-2xl font-semibold text-ink-800 mb-2">
          This page wandered off
        </h1>
        <p className="text-sm text-ink-500 mb-8">
          The page you're looking for doesn't exist, or has moved somewhere else.
        </p>
        <Link to="/" className="btn-secondary inline-flex">
          <ArrowLeft className="w-4 h-4 mr-2" strokeWidth={1.75} />
          Back to home
        </Link>
      </div>
    </div>
  )
}

export default ErrorPage
