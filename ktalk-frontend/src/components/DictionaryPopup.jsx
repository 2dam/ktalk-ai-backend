import { useEffect, useState } from 'react'
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

  return (
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
    </span>
  )
}

export default DictionaryPopup
