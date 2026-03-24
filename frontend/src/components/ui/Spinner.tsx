import clsx from 'clsx'

export default function Spinner({ className }: { className?: string }) {
  return (
    <div className={clsx('animate-spin rounded-full border-2 border-slate-600 border-t-indigo-400', className || 'w-6 h-6')} />
  )
}
