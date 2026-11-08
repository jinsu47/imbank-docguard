/**
 * 배너 뒤에 깔리는 그림: 문서 위의 값이 하나씩 가려지는 모습을 추상적으로 그립니다.
 * 내용을 전하지 않는 장식이라 보조 기술에는 숨깁니다.
 */
const SHEETS = [
  { x: 30, y: 70, rotate: -7, opacity: 0.55 },
  { x: 1180, y: 110, rotate: 6, opacity: 0.6 },
  { x: 620, y: 300, rotate: -2, opacity: 0.22 },
]

const ROWS = [
  { y: 44, width: 96, mask: false },
  { y: 78, width: 150, mask: true },
  { y: 112, width: 124, mask: false },
  { y: 146, width: 176, mask: true },
  { y: 180, width: 110, mask: false },
  { y: 214, width: 160, mask: true },
  { y: 248, width: 88, mask: false },
]

export function HeroArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 h-full w-full"
      viewBox="0 0 1440 520"
      preserveAspectRatio="xMidYMid slice"
    >
      {SHEETS.map((sheet) => (
        <g
          key={`${sheet.x}-${sheet.y}`}
          opacity={sheet.opacity}
          transform={`translate(${sheet.x} ${sheet.y}) rotate(${sheet.rotate} 115 160)`}
        >
          <rect
            width="230"
            height="310"
            fill="#fff"
            fillOpacity="0.05"
            stroke="#fff"
            strokeOpacity="0.28"
          />
          {ROWS.map((row) =>
            row.mask ? (
              <rect
                key={row.y}
                x="26"
                y={row.y}
                width={row.width}
                height="14"
                fill="#3ec08a"
                fillOpacity="0.75"
              />
            ) : (
              <rect
                key={row.y}
                x="26"
                y={row.y + 5}
                width={row.width}
                height="4"
                fill="#fff"
                fillOpacity="0.4"
              />
            ),
          )}
        </g>
      ))}
    </svg>
  )
}
