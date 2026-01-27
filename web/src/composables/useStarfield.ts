import { type Ref, onMounted, onUnmounted } from 'vue'

interface Star {
  x: number
  y: number
  size: number
  speed: number
}

/**
 * @description 创建一个动态的、分层的星空背景动画。
 * @param canvasRef 一个指向 Canvas 元素的 Ref。
 */
export function useStarfield(canvasRef: Ref<HTMLCanvasElement | null>) {
  let animationFrameId: number

  onMounted(() => {
    const canvas = canvasRef.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let stars1: Star[] = []
    let stars2: Star[] = []
    let stars3: Star[] = []

    const createStars = (count: number, size: number, speed: number): Star[] => {
      const arr: Star[] = []
      for (let i = 0; i < count; i++) {
        arr.push({
          x: Math.random() * canvas.width,
          y: Math.random() * canvas.height,
          size: Math.random() * size + 0.5,
          speed: Math.random() * speed + 0.2,
        })
      }
      return arr
    }

    const resizeCanvas = () => {
      canvas.width = window.innerWidth
      canvas.height = window.innerHeight
      stars1 = createStars(200, 0.8, 0.1)
      stars2 = createStars(100, 1.2, 0.25)
      stars3 = createStars(40, 1.8, 0.5)
    }

    const drawStars = (stars: Star[], opacity: number) => {
      ctx.fillStyle = `rgba(255,255,255,${opacity})`
      for (const star of stars) {
        star.y += star.speed
        if (star.y > canvas.height) {
          star.y = 0
          star.x = Math.random() * canvas.width
        }
        ctx.beginPath()
        ctx.arc(star.x, star.y, star.size, 0, Math.PI * 2)
        ctx.fill()
      }
    }

    const getBgColor = () => {
      const val = getComputedStyle(document.documentElement).getPropertyValue('--bg0')
      return val ? val.trim() : '#000'
    }

    const animate = () => {
      ctx.fillStyle = getBgColor()
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      drawStars(stars1, 0.4)
      drawStars(stars2, 0.6)
      drawStars(stars3, 0.9)
      animationFrameId = requestAnimationFrame(animate)
    }

    window.addEventListener('resize', resizeCanvas)
    resizeCanvas()
    animate()

    onUnmounted(() => {
      cancelAnimationFrame(animationFrameId)
      window.removeEventListener('resize', resizeCanvas)
    })
  })
}
