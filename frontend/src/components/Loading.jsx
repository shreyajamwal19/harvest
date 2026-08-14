function Loading() {
  return (
    <div
      className="flex flex-col items-center justify-center min-h-[300px] gap-4"
      role="status"
      aria-label="Loading"
    >
      <div className="flex gap-1.5">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="w-1.5 h-1.5 rounded-full bg-brick-400 animate-pulse"
            style={{ animationDelay: `${i * 150}ms`, animationDuration: '1.1s' }}
          />
        ))}
      </div>
      <span className="sr-only">Loading</span>
    </div>
  )
}

export default Loading
