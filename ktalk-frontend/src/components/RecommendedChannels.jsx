const RECOMMENDED_VIDEOS = [
  {
    channel: 'Talk To Me In Korean',
    videoId: 'srrrrjlUIxM',
    title: 'Top 10 Most-Watched Korean Movies of All Time'
  },
  {
    channel: 'Korean Unnie 한국언니',
    videoId: 'VcWWYdjePHE',
    title: '25 Phrases Native Koreans Actually Use!'
  },
  {
    channel: 'GO! Billy Korean',
    videoId: 'ntyAHtIIw1A',
    title: 'Beginner Korean Course #5: 한글 배우기'
  }
]

function RecommendedChannels() {
  return (
      <div style={{ marginBottom: '24px' }}>
        <h3 style={{ marginBottom: '10px', fontSize: '15px', color: '#666' }}>
          🎬 꾸준히 한국어를 가르치는 채널
        </h3>
        <div style={{ display: 'flex', gap: 'clamp(4px, 2vw, 12px)', width: '100%' }}>
          {RECOMMENDED_VIDEOS.map((video) => (
              <a key={video.videoId}
                 href={`https://www.youtube.com/watch?v=${video.videoId}`}
                 target="_blank" rel="noopener noreferrer"
                 style={{ flex: '1 1 0', minWidth: 0, color: 'inherit', textDecoration: 'none' }}>
                <div style={{
                  position: 'relative', paddingBottom: '56.25%', height: 0, borderRadius: '8px',
                  overflow: 'hidden', backgroundColor: '#000',
                  backgroundImage: `url(https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg)`,
                  backgroundSize: 'cover', backgroundPosition: 'center'
                }}>
                  <div style={{
                    position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
                    width: 'clamp(28px, 10vw, 44px)', height: 'clamp(28px, 10vw, 44px)',
                    borderRadius: '50%', backgroundColor: 'rgba(0,0,0,0.7)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center'
                  }}>
                    <div style={{
                      width: 0, height: 0, marginLeft: '3px',
                      borderTop: 'clamp(6px, 2.4vw, 10px) solid transparent',
                      borderBottom: 'clamp(6px, 2.4vw, 10px) solid transparent',
                      borderLeft: 'clamp(10px, 4vw, 16px) solid white'
                    }} />
                  </div>
                </div>
                <div style={{
                  fontSize: 'clamp(9px, 2.5vw, 12px)', color: '#999', marginTop: '5px',
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'
                }}>
                  {video.channel}
                </div>
              </a>
          ))}
        </div>
      </div>
  )
}

export default RecommendedChannels
