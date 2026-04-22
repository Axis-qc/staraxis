import { SHIP_CONSTANTS } from '../shipConstants.ts'

export interface Vec2d {
  x: number
  y: number
}

export interface ShipState {
  entityId: number
  position: Vec2d
  velocity: Vec2d | null
  movementTarget: Vec2d | null
  isMoving: boolean
  currentHeadingDeg: number
  targetHeadingDeg: number
  maxSpeed: number
  baseAcceleration: number
  bowAccelerationBonus: number
  turnRate: number
  lateralSpeedPenalty: number
  reverseSpeedPenalty: number
}

export interface WorldState {
  gameTimeSeconds: number
}

export class ShipMovementSystemFrontend {
  update(shipStates: ShipState[], worldState: WorldState, dtGameSeconds: number): void {
    for (const ship of shipStates) {
      this.updateShip(ship, worldState, dtGameSeconds)
    }
  }

  private updateShip(ship: ShipState, worldState: WorldState, dtGameSeconds: number): void {
    this.updateHeading(ship, dtGameSeconds)

    if (!ship.isMoving || !ship.movementTarget) {
      this.decelerateToStop(ship, dtGameSeconds, worldState)
      return
    }

    this.updateShipMovement(ship, dtGameSeconds, worldState)
  }

  private updateHeading(ship: ShipState, dtGameSeconds: number): void {
    const headingDiff = this.normalizeAngle(ship.targetHeadingDeg - ship.currentHeadingDeg)
    const maxTurn = ship.turnRate * dtGameSeconds

    if (Math.abs(headingDiff) <= maxTurn) {
      ship.currentHeadingDeg = ship.targetHeadingDeg
    } else {
      ship.currentHeadingDeg += Math.sign(headingDiff) * maxTurn
    }
    ship.currentHeadingDeg = this.normalizeAngle(ship.currentHeadingDeg)
  }

  private decelerateToStop(ship: ShipState, dtGameSeconds: number, _worldState: WorldState): void {
    if (!ship.velocity) {
      ship.velocity = { x: 0, y: 0 }
      return
    }

    const currentSpeed = this.calculateSpeed(ship.velocity)
    if (currentSpeed < 1.0) {
      ship.velocity = { x: 0, y: 0 }
      return
    }

    const decelAmount = ship.baseAcceleration * dtGameSeconds
    const newSpeed = Math.max(0, currentSpeed - decelAmount)
    const scale = newSpeed / currentSpeed

    ship.velocity = {
      x: ship.velocity.x * scale,
      y: ship.velocity.y * scale,
    }

    this.applyVelocity(ship, dtGameSeconds, _worldState)
  }

  private updateShipMovement(ship: ShipState, dtGameSeconds: number, worldState: WorldState): void {
    const target = ship.movementTarget!
    const dx = target.x - ship.position.x
    const dy = target.y - ship.position.y
    const distanceToTarget = Math.sqrt(dx * dx + dy * dy)

    if (distanceToTarget < SHIP_CONSTANTS.TARGET_ARRIVAL_THRESHOLD) {
      this.completeMoveAtTarget(ship, target)
      return
    }

    const moveDirX = dx / distanceToTarget
    const moveDirY = dy / distanceToTarget

    const headingRad = this.degToRad(ship.currentHeadingDeg)
    const bowX = Math.cos(headingRad)
    const bowY = Math.sin(headingRad)
    const dotProduct = moveDirX * bowX + moveDirY * bowY
    const angleDiff = this.radToDeg(Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct))))

    let effectiveMaxSpeed: number
    let effectiveAcceleration: number

    if (angleDiff < 45.0) {
      effectiveMaxSpeed = ship.maxSpeed
      effectiveAcceleration = ship.baseAcceleration + ship.bowAccelerationBonus
    } else if (angleDiff > 135.0) {
      effectiveMaxSpeed = ship.maxSpeed * ship.reverseSpeedPenalty
      effectiveAcceleration = ship.baseAcceleration
    } else {
      effectiveMaxSpeed = ship.maxSpeed * ship.lateralSpeedPenalty
      effectiveAcceleration = ship.baseAcceleration
    }

    const currentSpeed = ship.velocity ? this.calculateSpeed(ship.velocity) : 0
    const stopDistance = (currentSpeed * currentSpeed) / (2 * effectiveAcceleration)
    const needDecelerate = stopDistance >= distanceToTarget

    const targetVelX = moveDirX * effectiveMaxSpeed
    const targetVelY = moveDirY * effectiveMaxSpeed

    if (needDecelerate) {
      const decelAmount = effectiveAcceleration * dtGameSeconds
      const newSpeed = Math.max(0, currentSpeed - decelAmount)
      if (currentSpeed > SHIP_CONSTANTS.MIN_SPEED_THRESHOLD) {
        const scale = newSpeed / currentSpeed
        ship.velocity = {
          x: ship.velocity!.x * scale,
          y: ship.velocity!.y * scale,
        }
      } else {
        ship.velocity = { x: 0, y: 0 }
      }
    } else {
      const currentVelX = ship.velocity ? ship.velocity.x : 0
      const currentVelY = ship.velocity ? ship.velocity.y : 0
      const velDiffX = targetVelX - currentVelX
      const velDiffY = targetVelY - currentVelY
      const velDiff = Math.sqrt(velDiffX * velDiffX + velDiffY * velDiffY)

      if (velDiff < SHIP_CONSTANTS.VELOCITY_DIFF_THRESHOLD) {
        ship.velocity = { x: targetVelX, y: targetVelY }
      } else {
        const accelAmount = Math.min(velDiff, effectiveAcceleration * dtGameSeconds)
        const ratio = accelAmount / velDiff
        ship.velocity = {
          x: currentVelX + velDiffX * ratio,
          y: currentVelY + velDiffY * ratio,
        }
      }
    }

    const projectedTravelDistance = this.calculateSpeed(ship.velocity ?? { x: 0, y: 0 }) * dtGameSeconds
    if (projectedTravelDistance >= distanceToTarget) {
      this.completeMoveAtTarget(ship, target)
      return
    }

    this.applyVelocity(ship, dtGameSeconds, worldState)
  }

  private completeMoveAtTarget(ship: ShipState, target: Vec2d): void {
    ship.position = { x: target.x, y: target.y }
    ship.velocity = { x: 0, y: 0 }
    ship.isMoving = false
    ship.movementTarget = null
    ship.targetHeadingDeg = ship.currentHeadingDeg
  }

  private applyVelocity(ship: ShipState, dtGameSeconds: number, _worldState: WorldState): void {
    if (!ship.velocity) {
      return
    }

    ship.position = {
      x: ship.position.x + ship.velocity.x * dtGameSeconds,
      y: ship.position.y + ship.velocity.y * dtGameSeconds,
    }
  }

  private calculateSpeed(velocity: Vec2d): number {
    return Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
  }

  private normalizeAngle(angle: number): number {
    let normalized = angle
    while (normalized >= 180.0) {
      normalized -= 360.0
    }
    while (normalized < -180.0) {
      normalized += 360.0
    }
    return normalized
  }

  private degToRad(degrees: number): number {
    return (degrees * Math.PI) / 180.0
  }

  private radToDeg(radians: number): number {
    return (radians * 180.0) / Math.PI
  }
}
