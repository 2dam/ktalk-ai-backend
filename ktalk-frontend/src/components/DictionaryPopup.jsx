import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import axios from 'axios'
import { DICTIONARY_URL } from '../api'

/**
 * 단어 클릭 시 뜨는 뜻풀이 팝업. query가 바뀔 때마다 /api/dictionary를 다시 호출한다.
 */
function DictionaryPopup({ query, onClose }) {
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!query) return
    let cancelled = false
    setLoading(true)
    setError('')
    setEntries([])

    axios.get(DICTIONARY_URL, { params: { query, limit: 5 } })
      .then((res) => {
        if (cancelled) return
        if (res.data?.success) {
          setEntries(res.data.data ?? [])
        } else {
          setError(res.data?.message || '뜻을 찾지 못했어요.')
        }
      })
      .catch((err) => {
        if (cancelled) return
        setError(err.response?.data?.message || '사전을 불러오지 못했어요.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [query])

  // 팝업이 떠 있을 때 Esc로 닫기 (로그인 모달과 동일한 패턴)
  useEffect(() => {
    const onKey = (event) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // 이 팝업은 답안 선택 버튼처럼 클릭 가능한 조상 안에 있는 단어에서 열릴 수 있다.
  // 그 조상 안에 그대로 렌더링하면, 팝업이 열려 있는 동안 그 조상(또는 같은 그룹의
  // 형제 버튼)이 disabled되는 순간 팝업의 닫기 버튼·배경 클릭이 먹통이 되어 영원히
  // 닫히지 않는 버그가 생긴다. body로 포탈시켜 조상 요소의 상태와 완전히 분리한다.
  return createPortal(
    <span className="dictionary-popup-overlay" onClick={onClose}>
      <span className="dictionary-popup" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="dictionary-popup-close" onClick={onClose} aria-label="닫기">×</button>
        <div className="dictionary-popup-word">{query}</div>

        {loading && <div className="dictionary-popup-status">뜻을 찾는 중...</div>}
        {!loading && error && <div className="dictionary-popup-status dictionary-popup-error">⚠ {error}</div>}
        {!loading && !error && entries.length === 0 && (
          <div className="dictionary-popup-status">뜻풀이를 찾지 못했어요.</div>
        )}

        {!loading && entries.length > 0 && (
          <ul className="dictionary-popup-list">
            {entries.map((entry, idx) => (
              <li key={idx} className="dictionary-popup-entry">
                {entry.pos && <span className="dictionary-popup-pos">{entry.pos}</span>}
                <span>{entry.definition}</span>
              </li>
            ))}
          </ul>
        )}
      </span>
    </span>,
    document.body,
  )
}

export default DictionaryPopup
