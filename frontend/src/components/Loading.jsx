import { motion } from 'framer-motion'
import { Leaf } from 'lucide-react'

function Loading() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[300px] gap-4">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
        className="w-12 h-12 bg-sage-100 rounded-xl flex items-center justify-center"
      >
        <Leaf className="w-6 h-6 text-sage-600" />
      </motion.div>
      <motion.p
        animate={{ opacity: [0.5, 1, 0.5] }}
        transition={{ duration: 1.5, repeat: Infinity }}
        className="text-sm text-sage-500 font-medium"
      >
        Loading...
      </motion.p>
    </div>
  )
}

export default Loading
