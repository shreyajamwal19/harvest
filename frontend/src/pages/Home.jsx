import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowRight } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'

const capabilities = [
  {
    index: '01',
    title: 'Tell it what you have',
    description: 'List what\u2019s in your fridge or pantry, in plain language. No inventory to maintain.',
  },
  {
    index: '02',
    title: 'Get a real recipe',
    description: 'Harvest grounds every suggestion in an actual recipe \u2014 never an invented list of steps.',
  },
  {
    index: '03',
    title: 'Ask it anything mid-cook',
    description: 'Sauce split? Bread too dense? Ask Chef Brain directly and keep cooking.',
  },
]

function Home() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="min-h-[calc(100vh-4rem)]">
      {/* Hero */}
      <section className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 pt-16 pb-20 sm:pt-24 sm:pb-28">
        <div className="grid grid-cols-1 lg:grid-cols-[1.3fr_1fr] gap-12 items-end">
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
          >
            <span className="eyebrow">Harvest</span>
            <h1 className="mt-4 font-display text-5xl sm:text-6xl lg:text-[4.25rem] font-semibold text-ink-800 leading-[1.05] tracking-tight">
              Cook with what
              <br />
              you <span className="italic text-brick-500">already have.</span>
            </h1>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.1, ease: [0.22, 1, 0.36, 1] }}
            className="lg:pb-2"
          >
            <p className="text-lg text-ink-600 leading-relaxed">
              No shopping list required. Harvest turns the ingredients already in
              your kitchen into a recipe worth cooking tonight.
            </p>
            <div className="mt-6 flex flex-wrap items-center gap-4">
              <Link to={isAuthenticated ? '/chef' : '/signup'} className="btn-primary">
                {isAuthenticated ? 'Open Chef Brain' : 'Start cooking'}
                <ArrowRight className="w-4 h-4 ml-2" strokeWidth={1.75} />
              </Link>
              {!isAuthenticated && (
                <Link to="/login" className="text-sm font-medium text-ink-600 hover:text-ink-800 transition-colors">
                  Already have an account?
                </Link>
              )}
            </div>
          </motion.div>
        </div>
      </section>

      {/* Capabilities */}
      <section className="border-t border-ink-700/10">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-16 sm:py-20">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-x-8 gap-y-12">
            {capabilities.map((item, index) => (
              <motion.div
                key={item.title}
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: '-60px' }}
                transition={{ duration: 0.5, delay: index * 0.08, ease: [0.22, 1, 0.36, 1] }}
                className="card-flat"
              >
                <span className="font-display text-sm text-brick-500/70">{item.index}</span>
                <h3 className="font-display text-xl font-semibold text-ink-800 mt-2 mb-2">
                  {item.title}
                </h3>
                <p className="text-sm text-ink-500 leading-relaxed">
                  {item.description}
                </p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}

export default Home
