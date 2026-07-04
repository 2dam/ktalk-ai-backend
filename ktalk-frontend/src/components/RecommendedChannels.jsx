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
        <div style={{ display: 'flex', gap: '15px', flexWrap: 'wrap' }}>
          {RECOMMENDED_VIDEOS.map((video) => (
              <div key={video.videoId} style={{ flex: '1 1 250px', minWidth: '220px' }}>
                <div style={{ position: 'relative', paddingBottom: '56.25%', height: 0, borderRadius: '8px', overflow: 'hidden' }}>
                  <iframe
                      src={`https://www.youtube.com/embed/${video.videoId}`}
                      title={video.title}
                      style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', border: 0 }}
                      allowFullScreen
                  />
                </div>
                <div style={{ fontSize: '12px', color: '#999', marginTop: '5px' }}>{video.channel}</div>
              </div>
          ))}
        </div>
      </div>
  )
}

export default RecommendedChannels
