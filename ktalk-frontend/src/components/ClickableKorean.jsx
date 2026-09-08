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
            onClick={(event) => {
              // 이 단어가 답안 선택 버튼 등 클릭 가능한 조상 요소 안에 있을 수 있어서,
              // 전파를 막지 않으면 단어를 클릭했을 뿐인데 그 조상의 클릭(예: 정답 제출)까지
              // 같이 실행돼버린다. 실제로 이 때문에 조상 버튼이 disabled되면서 사전
              // 팝업이 그 안에 갇혀 닫히지 않는 버그가 있었다.
              event.stopPropagation()
              setActiveQuery(clean)
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                event.stopPropagation()
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
