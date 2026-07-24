import { useState } from 'react'
import axios from 'axios'
import { API_BASE, authHeaders } from '../api'
import ClickableKorean from './ClickableKorean'
import CurriculumProblemCard from './CurriculumProblemCard'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`

function playSegmentsSequentially(segments) {
  return segments.reduce(
    (chain, segment) => chain.then(() => new Promise((resolve) => {
      const audio = new Audio(`data:audio/mp3;base64,${segment.audioContent}`)
      audio.onended = resolve
      audio.onerror = resolve
      audio.play().catch(resolve)
    })),
    Promise.resolve(),
  )
}

/**
 * 지문 하나(듣기 대화 또는 읽기 지문) + 거기 딸린 문제들. 듣기 지문은 재생 버튼을 누르면
 * 화자별 음성을 순서대로 이어서 재생한다.
 */
function CurriculumPassageCard({ passage, index }) {
  const [playing, setPlaying] = useState(false)
  const [audioError, setAudioError] = useState('')

  const handlePlay = async () => {
    setPlaying(true)
    setAudioError('')
    try {
      const res = await axios.get(`${CURRICULUM_URL}/passages/${passage.id}/audio`, { headers: authHeaders() })
      if (res.data?.success) {
        await playSegmentsSequentially(res.data.data)
      } else {
        setAudioError(res.data?.message || '음성을 만들지 못했어요.')
      }
    } catch (err) {
      setAudioError(err.response?.data?.message || '음성을 만들지 못했어요.')
    } finally {
      setPlaying(false)
    }
  }

  return (
    <div style={{ padding: '16px', border: '1px solid #eee', borderRadius: '10px', marginBottom: '14px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px', flexWrap: 'wrap' }}>
        <span style={{
          fontSize: '12px', padding: '2px 8px', borderRadius: '999px',
          backgroundColor: '#eef2ff', color: '#4338ca',
        }}>
          {index + 1}. {passage.category === 'LISTENING' ? '듣기' : '읽기'}
          {passage.subType ? ` · ${passage.subType}` : ''}
        </span>
        {passage.hasAudio && (
          <button
            type="button"
            onClick={handlePlay}
            disabled={playing}
            style={{
              border: 'none', background: 'none', cursor: playing ? 'not-allowed' : 'pointer',
              fontSize: '13px', color: '#2563eb', padding: 0,
            }}
          >
            {playing ? '🔊 재생 중...' : '🔊 대화 듣기'}
          </button>
        )}
      </div>

      {audioError && <p style={{ color: '#dc3545', fontSize: '12px' }}>⚠ {audioError}</p>}

      <div style={{ whiteSpace: 'pre-wrap', fontSize: '14px', lineHeight: 1.7, marginBottom: '10px' }}>
        <ClickableKorean text={passage.passageText} />
      </div>

      {passage.problems.map((problem) => (
        <CurriculumProblemCard key={problem.id} problem={problem} />
      ))}
    </div>
  )
}

export default CurriculumPassageCard
