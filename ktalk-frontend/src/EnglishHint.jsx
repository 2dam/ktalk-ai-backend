import { useCallback, useMemo, useState } from 'react'
import { EnglishHintContext, useEnglishHint } from './englishHintContext'

const STORAGE_KEY = 'ktalk.englishHint'

function readInitial() {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'off'
  } catch {
    return true
  }
}

export function EnglishHintProvider({ children }) {
  const [enabled, setEnabled] = useState(readInitial)

  const toggle = useCallback(() => {
    setEnabled((prev) => {
      const next = !prev
      try {
        localStorage.setItem(STORAGE_KEY, next ? 'on' : 'off')
      } catch {
        // 저장이 막힌 환경(사생활 보호 모드 등)에서도 화면 토글 자체는 동작해야 한다.
      }
      return next
    })
  }, [])

  const value = useMemo(() => ({ enabled, toggle }), [enabled, toggle])
  return <EnglishHintContext.Provider value={value}>{children}</EnglishHintContext.Provider>
}

/**
 * 한글 문구 뒤에 붙여 아래 줄에 옅은 영어를 보여준다. 켜져 있지 않으면 한글만 그대로 렌더링한다.
 *   <Bi en="Start today's lesson">오늘의 학습 시작하기</Bi>
 */
export function Bi({ en, children }) {
  const { enabled } = useEnglishHint()
  if (!enabled || !en) return children
  return (
    <>
      {children}
      <small className="bi-en" lang="en">{en}</small>
    </>
  )
}

export function EnglishHintToggle() {
  const { enabled, toggle } = useEnglishHint()
  return (
    <button
      type="button"
      className={`en-toggle${enabled ? ' on' : ''}`}
      onClick={toggle}
      aria-pressed={enabled}
      title={enabled ? 'English hints: ON' : 'English hints: OFF'}
    >
      EN
    </button>
  )
}
