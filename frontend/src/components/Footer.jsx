import { Link } from 'react-router-dom'
import { Leaf, Heart } from 'lucide-react'

function Footer() {
  return (
    <footer className="bg-sage-800 text-cream-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Brand */}
          <div>
            <Link to="/" className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 bg-sage-600 rounded-lg flex items-center justify-center">
                <Leaf className="w-4 h-4 text-white" />
              </div>
              <span className="font-display text-lg font-semibold text-white">
                Harvest
              </span>
            </Link>
            <p className="text-sm text-sage-300 leading-relaxed max-w-xs">
              Cook with what you already have. Reduce waste, save money, and discover delicious recipes from your pantry.
            </p>
          </div>

          {/* Links */}
          <div>
            <h4 className="font-display text-sm font-semibold text-white mb-4 uppercase tracking-wider">
              Quick Links
            </h4>
            <ul className="space-y-2">
              <li>
                <Link to="/" className="text-sm text-sage-300 hover:text-white transition-colors">
                  Home
                </Link>
              </li>
              <li>
                <Link to="/login" className="text-sm text-sage-300 hover:text-white transition-colors">
                  Log in
                </Link>
              </li>
              <li>
                <Link to="/signup" className="text-sm text-sage-300 hover:text-white transition-colors">
                  Sign up
                </Link>
              </li>
            </ul>
          </div>

          {/* Contact */}
          <div>
            <h4 className="font-display text-sm font-semibold text-white mb-4 uppercase tracking-wider">
              Connect
            </h4>
            <p className="text-sm text-sage-300">
              hello@harvest.app
            </p>
          </div>
        </div>

        <div className="mt-10 pt-6 border-t border-sage-700 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-xs text-sage-400">
            &copy; {new Date().getFullYear()} Harvest. All rights reserved.
          </p>
          <p className="text-xs text-sage-400 flex items-center gap-1">
            Made with <Heart className="w-3 h-3 text-terracotta-400" /> for home cooks everywhere
          </p>
        </div>
      </div>
    </footer>
  )
}

export default Footer
