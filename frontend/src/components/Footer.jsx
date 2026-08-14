import { Link } from 'react-router-dom'

function Footer() {
  return (
    <footer className="bg-ink-800 text-paper-300">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-14">
        <div className="grid grid-cols-1 md:grid-cols-[1.4fr_1fr_1fr] gap-10">
          <div>
            <span className="font-display text-xl font-semibold text-paper-50">Harvest</span>
            <p className="mt-3 text-sm text-paper-400 leading-relaxed max-w-xs">
              Cook with what you already have. Reduce waste, save money, and discover
              what your pantry can become.
            </p>
          </div>

          <div>
            <h4 className="eyebrow text-gold-300 mb-4">Navigate</h4>
            <ul className="space-y-2.5">
              <li>
                <Link to="/" className="text-sm text-paper-300 hover:text-paper-50 transition-colors">
                  Home
                </Link>
              </li>
              <li>
                <Link to="/login" className="text-sm text-paper-300 hover:text-paper-50 transition-colors">
                  Log in
                </Link>
              </li>
              <li>
                <Link to="/signup" className="text-sm text-paper-300 hover:text-paper-50 transition-colors">
                  Sign up
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="eyebrow text-gold-300 mb-4">Say hello</h4>
            <p className="text-sm text-paper-300">hello@harvest.app</p>
          </div>
        </div>

        <div className="mt-10 pt-6 border-t border-paper-50/10 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="text-xs text-paper-400">
            &copy; {new Date().getFullYear()} Harvest.
          </p>
          <p className="text-xs text-paper-400">
            Made for home cooks who open the fridge before the recipe box.
          </p>
        </div>
      </div>
    </footer>
  )
}

export default Footer
