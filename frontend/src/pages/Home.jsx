import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ChefHat, Leaf, Sparkles } from 'lucide-react'

function Home() {
  return (
    <div className="min-h-[calc(100vh-4rem)]">
      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 sm:py-28 lg:py-36">
          <div className="max-w-3xl mx-auto text-center">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6 }}
            >
              <div className="inline-flex items-center gap-2 px-4 py-2 bg-sage-100 text-sage-700 rounded-full text-sm font-medium mb-8">
                <Sparkles className="w-4 h-4" />
                Coming soon
              </div>
            </motion.div>

            <motion.h1
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
              className="font-display text-5xl sm:text-6xl lg:text-7xl font-bold text-sage-900 leading-[1.1] mb-6"
            >
              Cook with what you{' '}
              <span className="text-sage-600 italic">already have</span>
            </motion.h1>

            <motion.p
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="text-lg sm:text-xl text-sage-600 leading-relaxed mb-10 max-w-2xl mx-auto"
            >
              Harvest transforms your pantry into a world of recipes. Reduce food waste,
              save money, and discover dishes you never knew you could make.
            </motion.p>

            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
              className="flex flex-col sm:flex-row items-center justify-center gap-4"
            >
              <Link to="/signup" className="btn-primary w-full sm:w-auto">
                <ChefHat className="w-5 h-5 mr-2" />
                Start cooking
              </Link>
              <Link to="/login" className="btn-secondary w-full sm:w-auto">
                Already have an account?
              </Link>
            </motion.div>
          </div>
        </div>

        {/* Decorative elements */}
        <div className="absolute top-20 left-10 w-64 h-64 bg-sage-200/30 rounded-full blur-3xl -z-10" />
        <div className="absolute bottom-20 right-10 w-80 h-80 bg-terracotta-200/20 rounded-full blur-3xl -z-10" />
      </section>

      {/* Features Preview */}
      <section className="py-20 bg-white border-y border-cream-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-16">
            <h2 className="font-display text-3xl sm:text-4xl font-bold text-sage-900 mb-4">
              What&apos;s coming
            </h2>
            <p className="text-sage-600 max-w-xl mx-auto">
              A complete kitchen companion built to help you make the most of every ingredient.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {[
              {
                icon: <Leaf className="w-6 h-6 text-sage-600" />,
                title: 'Smart Pantry',
                description: 'Track ingredients, expiry dates, and quantities with intelligent suggestions.',
              },
              {
                icon: <ChefHat className="w-6 h-6 text-terracotta-600" />,
                title: 'Recipe Discovery',
                description: 'Find recipes that match exactly what you have on hand.',
              },
              {
                icon: <Sparkles className="w-6 h-6 text-sage-600" />,
                title: 'AI Chef',
                description: 'Get personalized cooking tips and creative substitutions from our AI.',
              },
            ].map((feature, index) => (
              <motion.div
                key={feature.title}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
                className="card text-center hover:shadow-md transition-shadow"
              >
                <div className="w-12 h-12 bg-cream-100 rounded-xl flex items-center justify-center mx-auto mb-4">
                  {feature.icon}
                </div>
                <h3 className="font-display text-lg font-semibold text-sage-900 mb-2">
                  {feature.title}
                </h3>
                <p className="text-sm text-sage-600 leading-relaxed">
                  {feature.description}
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
