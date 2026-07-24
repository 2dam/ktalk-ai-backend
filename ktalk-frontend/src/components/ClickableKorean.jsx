import { useState } from 'react'
import DictionaryPopup from './DictionaryPopup'

// 사전 검색 전에 앞뒤에 붙은 따옴표/문장부호를 최대한 떼어낸다.
// 완벽한 형태소 분석(조사 분리)은 아니라서 "학교는" 같은 단어는 못 찾을 수 있다.
const TRIM_PATTERN = /^["'“”‘’(),.!?~…]+|["'“”‘’(),.!?~…]+$/g

function cleanToken(token) {
  return token.replace(TRIM_PATTERN, '')
}

/**
 * 문장/단어를 공백 기준으로 나눠 클릭 가능한 조각으로 렌더링한다.
 * 아무 조각이나 클릭하면 그 단어로 사전 팝업(DictionaryPopup)을 띄운다.
 */
function ClickableKorean({ text, style }) {
  const [activeQuery, setActiveQuery] = useState(null)

  if (!text) return null

  const tokens = text.split(/(\s+)/)

  return (
    <span style={style}>
      {tokens.map((token, idx) => {
        if (token === '' || /^\s+$/.test(token)) {
          return <span key={idx}>{token}</span>
        }
        const clean = cleanToken(token)
        if (!clean) {
          return <span key={idx}>{token}</span>
        }
        return (
          <span
            key={idx}
            className="clickable-word"
            role="button"
            tabIndex={0}
            onClick={() => setActiveQuery(clean)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                setActiveQuery(clean)
              }
            }}
          >
            {token}
          </span>
        )
      })}
      {activeQuery && (
        <DictionaryPopup query={activeQuery} onClose={() => setActiveQuery(null)} />
      )}
    </span>
  )
}

export default ClickableKorean
