import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useLanguage } from '../context/LanguageContext'
import { Shield, Globe, Zap, Eye, CheckCircle, ChevronRight, ChevronLeft } from 'lucide-react'

const slides = [
  {
    id: 'welcome',
    icon: Shield,
    titleKey: 'onboardingWelcome',
    description: 'Bypass censorship and protect your privacy with military-grade encryption. Built for Africa.',
    color: 'from-green-500 to-emerald-600',
    image: '🛡️',
  },
  {
    id: 'privacy',
    icon: Eye,
    titleKey: 'onboardingPrivacy',
    description: 'Zero-logs policy. We never track, store, or sell your data. Your online activity stays private.',
    color: 'from-blue-500 to-cyan-600',
    image: '🔒',
  },
  {
    id: 'speed',
    icon: Zap,
    titleKey: 'onboardingSpeed',
    description: 'Servers across Africa with data compression. Save up to 70% on mobile data costs.',
    color: 'from-yellow-500 to-orange-500',
    image: '⚡',
  },
  {
    id: 'stealth',
    icon: Eye,
    titleKey: 'onboardingStealth',
    description: '9 obfuscation protocols to bypass deep packet inspection. Panic button for emergencies.',
    color: 'from-purple-500 to-pink-600',
    image: '👻',
  },
  {
    id: 'ready',
    icon: CheckCircle,
    titleKey: 'onboardingReady',
    description: 'One tap to connect. Access any website, anywhere. Digital freedom starts now.',
    color: 'from-green-600 to-teal-600',
    image: '✅',
  },
]

export default function Onboarding({ onComplete }) {
  const [currentSlide, setCurrentSlide] = useState(0)
  const { t, language, changeLanguage, availableLanguages } = useLanguage()

  const nextSlide = () => {
    if (currentSlide < slides.length - 1) {
      setCurrentSlide(prev => prev + 1)
    } else {
      onComplete()
    }
  }

  const prevSlide = () => {
    if (currentSlide > 0) {
      setCurrentSlide(prev => prev - 1)
    }
  }

  const slide = slides[currentSlide]
  const isLastSlide = currentSlide === slides.length - 1

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 to-slate-800 flex flex-col">
      {/* Language selector */}
      <div className="p-4 flex justify-end">
        <select
          value={language}
          onChange={(e) => changeLanguage(e.target.value)}
          className="bg-slate-700 text-white px-4 py-2 rounded-lg border border-slate-600 focus:outline-none focus:ring-2 focus:ring-green-500"
        >
          {availableLanguages.map(lang => (
            <option key={lang.code} value={lang.code}>
              {lang.flag} {lang.name}
            </option>
          ))}
        </select>
      </div>

      {/* Skip button */}
      <div className="px-4">
        <button
          onClick={onComplete}
          className="text-slate-400 hover:text-white transition-colors"
        >
          {t('skip')} →
        </button>
      </div>

      {/* Slide content */}
      <div className="flex-1 flex flex-col items-center justify-center px-6">
        <AnimatePresence mode="wait">
          <motion.div
            key={slide.id}
            initial={{ opacity: 0, x: 50 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -50 }}
            transition={{ duration: 0.3 }}
            className="text-center max-w-md"
          >
            {/* Icon */}
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ delay: 0.2, type: 'spring' }}
              className={`w-32 h-32 mx-auto mb-8 rounded-full bg-gradient-to-br ${slide.color} flex items-center justify-center text-6xl shadow-2xl`}
            >
              {slide.image}
            </motion.div>

            {/* Title */}
            <h1 className="text-3xl font-bold text-white mb-4">
              {t(slide.titleKey)}
            </h1>

            {/* Description */}
            <p className="text-slate-300 text-lg leading-relaxed">
              {slide.description}
            </p>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Progress dots */}
      <div className="flex justify-center gap-2 mb-8">
        {slides.map((_, index) => (
          <button
            key={index}
            onClick={() => setCurrentSlide(index)}
            className={`w-3 h-3 rounded-full transition-all ${
              index === currentSlide
                ? 'bg-green-500 w-8'
                : 'bg-slate-600 hover:bg-slate-500'
            }`}
          />
        ))}
      </div>

      {/* Navigation buttons */}
      <div className="p-6 flex justify-between">
        <button
          onClick={prevSlide}
          disabled={currentSlide === 0}
          className={`flex items-center gap-2 px-6 py-3 rounded-xl font-medium transition-all ${
            currentSlide === 0
              ? 'opacity-0 pointer-events-none'
              : 'bg-slate-700 text-white hover:bg-slate-600'
          }`}
        >
          <ChevronLeft className="w-5 h-5" />
          Back
        </button>

        <button
          onClick={nextSlide}
          className={`flex items-center gap-2 px-8 py-3 rounded-xl font-medium transition-all ${
            isLastSlide
              ? 'bg-gradient-to-r from-green-500 to-emerald-600 text-white hover:shadow-lg hover:shadow-green-500/30'
              : 'bg-green-600 text-white hover:bg-green-500'
          }`}
        >
          {isLastSlide ? t('getStarted') : t('next')}
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>
    </div>
  )
}
