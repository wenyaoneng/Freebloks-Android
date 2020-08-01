package de.saschahlusiak.freebloks.view.scene

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.opengl.GLUtils
import de.saschahlusiak.freebloks.Global
import de.saschahlusiak.freebloks.R
import de.saschahlusiak.freebloks.model.Mirrorable
import de.saschahlusiak.freebloks.model.Orientation
import de.saschahlusiak.freebloks.model.Stone
import de.saschahlusiak.freebloks.model.Turn
import de.saschahlusiak.freebloks.view.BoardRenderer
import de.saschahlusiak.freebloks.view.FreebloksRenderer
import de.saschahlusiak.freebloks.view.SimpleModel
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

class CurrentStone(private val scene: Scene) : SceneElement {
    enum class Status {
        IDLE, DRAGGING, ROTATING, FLIPPING_HORIZONTAL, FLIPPING_VERTICAL
    }

    var stone: Stone? = null
    private var currentColor = 0
    private val pos = PointF()

    /* has the stone been moved since it was touched? */
    var hasMoved = false

    /* is the stone commitable if it has not been moved? */
    private var canCommit = false

    private var isValid = false
    private var stoneRelX = 0f
    private var stoneRelY = 0f
    private var rotateAngle = 0f
    private val texture = IntArray(3)
    private val overlay: SimpleModel
    var status: Status = Status.IDLE
    private var orientation = Orientation()
    val hoverHeightLow = 0.55f
    val hoverHeightHigh = 0.55f
    private var fieldPoint = PointF()
    private var screenPoint = PointF()

    init {
        overlay = SimpleModel(4, 2, false).apply {
            addVertex(-overlay_radius, 0f, overlay_radius, 0f, -1f, 0f, 0f, 0f)
            addVertex(+overlay_radius, 0f, overlay_radius, 0f, -1f, 0f, 1f, 0f)
            addVertex(+overlay_radius, 0f, -overlay_radius, 0f, -1f, 0f, 1f, 1f)
            addVertex(-overlay_radius, 0f, -overlay_radius, 0f, -1f, 0f, 0f, 1f)
            addIndex(0, 1, 2)
            addIndex(0, 2, 3)
            commit()
        }
    }

    fun updateTexture(context: Context, gl: GL10) {
        gl.glGenTextures(3, texture, 0)

        var bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.stone_overlay_green)
        gl.glBindTexture(GL10.GL_TEXTURE_2D, texture[0])

        if (gl is GL11) {
            gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR_MIPMAP_NEAREST)
            gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE.toFloat())
        } else {
            gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR)
        }
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR)
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.stone_overlay_red)
        gl.glBindTexture(GL10.GL_TEXTURE_2D, texture[1])
        if (gl is GL11) {
            gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR_MIPMAP_NEAREST)
            gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE.toFloat())
        } else {
            gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR)
        }
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR)
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.stone_overlay_shadow)
        gl.glBindTexture(GL10.GL_TEXTURE_2D, texture[2])
        if (gl is GL11) {
            gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR_MIPMAP_NEAREST)
            gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE.toFloat())
        } else {
            gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR)
        }
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR)
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    //	final float diffuse_red[] = { 1.0f, 0.5f, 0.5f, 1.0f };
    //	final float diffuse_green[] = { 0.5f, 1.0f, 0.5f, 1.0f };
    //	final float diffuse_white[] = { 1.0f, 1.0f, 1.0f, 0.6f };
    @Synchronized
    fun render(renderer: FreebloksRenderer, gl: GL11) {
        val stone = stone ?: return

        var hoverHeight = if (status == Status.IDLE) hoverHeightLow else hoverHeightHigh
        val offset = stone.shape.size.toFloat() - 1.0f
        val currentPlayer = scene.game.currentPlayer
        if (currentPlayer < 0) return

        if (status == Status.FLIPPING_HORIZONTAL ||
            status == Status.FLIPPING_VERTICAL) hoverHeight += abs(sin(rotateAngle / 180 * Math.PI) * overlay_radius * 0.4f).toFloat()

        gl.glDisable(GL10.GL_CULL_FACE)
        gl.glPushMatrix()
        gl.glTranslatef(
            BoardRenderer.stoneSize * ((-(scene.board.width - 1)).toFloat() + 2.0f * pos.x + offset), 0f,
            BoardRenderer.stoneSize * ((-(scene.board.width - 1)).toFloat() + 2.0f * pos.y + offset))

        /* STONE SHADOW */gl.glPushMatrix()
        /* TODO: remove this and always show the board at the exact same angle,
		 * so we always have light coming from top left */
        /* TODO: merge with BoardRenderer.renderShadow() */
        val baseAngle = scene.boardObject.baseAngle
        gl.glRotatef(baseAngle, 0f, 1f, 0f)
        gl.glTranslatef(2.5f * hoverHeight * 0.08f, 0f, 2.0f * hoverHeight * 0.08f)
        gl.glRotatef(-baseAngle, 0f, 1f, 0f)
        if (status == Status.ROTATING) gl.glRotatef(rotateAngle, 0f, 1f, 0f)
        if (status == Status.FLIPPING_HORIZONTAL) gl.glRotatef(rotateAngle, 0f, 0f, 1f)
        if (status == Status.FLIPPING_VERTICAL) gl.glRotatef(rotateAngle, 1f, 0f, 0f)
        gl.glScalef(1.09f, 0.01f, 1.09f)
        gl.glTranslatef(
            -BoardRenderer.stoneSize * offset, 0f,
            -BoardRenderer.stoneSize * offset)
        renderer.boardRenderer.renderShapeShadow(gl, currentPlayer, stone.shape, orientation, 0.80f)
        gl.glPopMatrix()
        gl.glEnable(GL10.GL_TEXTURE_2D)
        gl.glEnable(GL10.GL_BLEND)
        gl.glDisable(GL10.GL_LIGHTING)
        gl.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
        overlay.bindBuffers(gl)

        /* OVERLAY SHADOW */gl.glPushMatrix()
        gl.glBindTexture(GL10.GL_TEXTURE_2D, texture[2])
        gl.glRotatef(baseAngle, 0f, 1f, 0f)
        gl.glTranslatef(2.5f * hoverHeight * 0.08f, 0f, 2.0f * hoverHeight * 0.08f)
        gl.glRotatef(-baseAngle, 0f, 1f, 0f)
        if (status == Status.ROTATING) gl.glRotatef(rotateAngle, 0f, 1f, 0f)
        if (status == Status.FLIPPING_HORIZONTAL) gl.glRotatef(rotateAngle, 0f, 0f, 1f)
        if (status == Status.FLIPPING_VERTICAL) gl.glRotatef(rotateAngle, 1f, 0f, 0f)
        gl.glScalef(1.0f, 0.01f, 1.0f)
        overlay.drawElements(gl, GL10.GL_TRIANGLES)
        gl.glPopMatrix()

        /* OVERLAY */gl.glPushMatrix()
        gl.glTranslatef(0f, hoverHeight, 0f)
        if (status == Status.ROTATING) gl.glRotatef(rotateAngle, 0f, 1f, 0f)
        if (status == Status.FLIPPING_HORIZONTAL) gl.glRotatef(rotateAngle, 0f, 0f, 1f)
        if (status == Status.FLIPPING_VERTICAL) gl.glRotatef(rotateAngle, 1f, 0f, 0f)
        gl.glBindTexture(GL10.GL_TEXTURE_2D, if (isValid) texture[0] else texture[1])
        overlay.drawElements(gl, GL10.GL_TRIANGLES)
        gl.glEnable(GL10.GL_CULL_FACE)
        gl.glEnable(GL10.GL_LIGHTING)
        gl.glDisable(GL10.GL_TEXTURE_2D)


        /* STONE */gl.glPopMatrix()
        gl.glTranslatef(0f,
            hoverHeight, 0f)
        if (status == Status.ROTATING) gl.glRotatef(rotateAngle, 0f, 1f, 0f)
        if (status == Status.FLIPPING_HORIZONTAL) gl.glRotatef(rotateAngle, 0f, 0f, 1f)
        if (status == Status.FLIPPING_VERTICAL) gl.glRotatef(rotateAngle, 1f, 0f, 0f)
        gl.glTranslatef(
            -BoardRenderer.stoneSize * offset, 0f,
            -BoardRenderer.stoneSize * offset)
        gl.glEnable(GL10.GL_DEPTH_TEST)
        renderer.boardRenderer.renderShape(gl, currentColor, stone.shape, orientation,
            if (status != Status.IDLE || isValid) 1.0f else BoardRenderer.defaultStoneAlpha)
        gl.glPopMatrix()
    }

    fun moveTo(x: Float, y: Float): Boolean {
        var x = x
        var y = y
        if (stone == null) return false

        /* provide a weird but nice pseudo snapping feeling */if (scene.hasAnimations()) {
            var r = x - Math.floor(x.toDouble()).toFloat()
            if (r < 0.5f) {
                r *= 2.0f
                r = r * r * r * r
                r /= 2.0f
            } else {
                r = 1.0f - r
                r *= 2.0f
                r = r * r * r * r
                r /= 2.0f
                r = 1.0f - r
            }
            x = (Math.floor(x.toDouble()) + r).toFloat()
            r = y - Math.floor(y.toDouble()).toFloat()
            if (r < 0.5f) {
                r *= 2.0f
                r = r * r * r
                r /= 2.0f
            } else {
                r = 1.0f - r
                r *= 2.0f
                r = r * r * r
                r /= 2.0f
                r = 1.0f - r
            }
            y = (Math.floor(y.toDouble()) + r).toFloat()
        } else {
            x = Math.floor(x + 0.5f.toDouble()).toFloat()
            y = Math.floor(y + 0.5f.toDouble()).toFloat()
        }

        /* FIXME: lock stone inside 3 top walls, when board is always in the same orientation */
        /*
		for (int i = 0; i < stone.getSize(); i++)
			for (int j = 0; j < stone.getSize(); j++) {
				if (stone.getStoneField(j, i) == Stone.STONE_FIELD_ALLOWED) {
					if (x + i < 0)
						x = -i;
//					if (y + j < 0)
//						y = -j;

					if (x + i + 1 >= model.spiel.width)
						x = model.spiel.width - i - 1;
					if (y + j + 1 >= model.spiel.height)
						y = model.spiel.height - j - 1;
				}
			}
			*/if (Math.floor(0.5f + pos.x.toDouble()) != Math.floor(0.5f + x.toDouble()) || Math.floor(pos.y + 0.5f.toDouble()) != Math.floor(0.5f + y.toDouble())) {
            pos.x = x
            pos.y = y
            return true
        }
        pos.x = x
        pos.y = y
        return false
    }

    @Synchronized
    override fun handlePointerDown(m: PointF): Boolean {
        status = Status.IDLE
        hasMoved = false
        canCommit = true
        val stone = stone

        if (stone != null) {
            fieldPoint.x = m.x
            fieldPoint.y = m.y
            scene.modelToBoard(fieldPoint)
            screenPoint.x = fieldPoint.x
            screenPoint.y = fieldPoint.y
            stoneRelX = pos.x - fieldPoint.x + stone.shape.size / 2
            stoneRelY = pos.y - fieldPoint.y + stone.shape.size / 2

//			Log.d(tag, "rel = (" + stone_rel_x + " / " + stone_rel_y+ ")");
            if (abs(stoneRelX) <= overlay_radius + 3.0f && abs(stoneRelY) <= overlay_radius + 3.0f) {
                if (abs(stoneRelX) > overlay_radius - 1.5f && abs(stoneRelY) < 2.5f ||
                    abs(stoneRelX) < 2.5f && abs(stoneRelY) > overlay_radius - 1.5f) {
                    status = Status.ROTATING
                    rotateAngle = 0.0f
                } else {
                    status = Status.DRAGGING
                }
                return true
            }
        }
        return false
    }

    @Synchronized
    override fun handlePointerMove(m: PointF): Boolean {
        if (status == Status.IDLE) return false
        val stone = stone ?: return false
        fieldPoint.x = m.x
        fieldPoint.y = m.y
        scene.modelToBoard(fieldPoint)
        if (status == Status.DRAGGING) {
            val THRESHOLD = 1.0f
            val x = fieldPoint.x + stoneRelX - stone.shape.size / 2
            val y = fieldPoint.y + stoneRelY - stone.shape.size / 2
            if (!hasMoved && Math.abs(screenPoint.x - fieldPoint.x) < THRESHOLD && Math.abs(screenPoint.y - fieldPoint.y) < THRESHOLD) return true
            val mv = snap(x, y, false)
            hasMoved = hasMoved or mv
            if (mv || scene.hasAnimations()) {
                scene.invalidate()
            }
        }
        if (status == Status.ROTATING) {
            val rx = pos.x - fieldPoint.x + stone.shape.size / 2
            val ry = pos.y - fieldPoint.y + stone.shape.size / 2
            val a1 = Math.atan2(stoneRelY.toDouble(), stoneRelX.toDouble()).toFloat()
            val a2 = Math.atan2(ry.toDouble(), rx.toDouble()).toFloat()
            rotateAngle = (a1 - a2) * 180.0f / Math.PI.toFloat()
            if (Math.abs(rx) + Math.abs(ry) < overlay_radius * 0.9f && Math.abs(rotateAngle) < 25.0f) {
                rotateAngle = 0.0f
                status = if (Math.abs(stoneRelY) < 3) Status.FLIPPING_HORIZONTAL else Status.FLIPPING_VERTICAL
            }
            scene.invalidate()
        }
        if (status == Status.FLIPPING_HORIZONTAL) {
            val rx = pos.x - fieldPoint.x + stone.shape.size / 2
            var p: Float
            p = (stoneRelX - rx) / (stoneRelX * 2.0f)
            if (p < 0) p = 0f
            if (p > 1) p = 1f
            if (stoneRelX > 0) p = -p
            rotateAngle = p * 180
            scene.invalidate()
        }
        if (status == Status.FLIPPING_VERTICAL) {
            val ry = pos.y - fieldPoint.y + stone.shape.size / 2
            var p: Float
            p = (stoneRelY - ry) / (stoneRelY * 2.0f)
            if (p < 0) p = 0f
            if (p > 1) p = 1f
            if (stoneRelY < 0) p = -p
            rotateAngle = p * 180
            scene.invalidate()
        }
        return true
    }

    private fun rotateLef() {
        val stone = stone ?: return
        orientation = orientation.rotatedLeft(stone.shape.rotatable)
    }

    private fun rotateRight() {
        val stone = stone ?: return
        orientation = orientation.rotatedRight(stone.shape.rotatable)
    }

    private fun mirrorOverX() {
        val stone = stone ?: return
        if (stone.shape.mirrorable === Mirrorable.Not) return
        orientation = orientation.mirroredVertically()
    }

    private fun mirrorOverY() {
        if (stone?.shape?.mirrorable === Mirrorable.Not) return
        orientation = orientation.mirroredHorizontally()
    }

    private fun isValidTurn(x: Float, y: Float): Boolean {
        val stone = stone ?: return false
        if (!scene.game.isLocalPlayer()) return false
        return scene.board.isValidTurn(
            stone.shape,
            scene.game.currentPlayer,
            floor(y + 0.5f).toInt(),
            floor(x + 0.5f).toInt(),
            orientation
        )
    }

    private fun snap(x: Float, y: Float, forceSound: Boolean): Boolean {
        val hasMoved: Boolean
        if (!scene.snapAid) {
            hasMoved = moveTo(x, y)
            isValid = isValidTurn(x, y)
            if (isValid && (hasMoved || forceSound)) {
                if (!scene.soundPool.play(scene.soundPool.SOUND_CLICK3, 1.0f, 1.0f)) scene.vibrate(Global.VIBRATE_STONE_SNAP)
            }
            return hasMoved
        }
        if (isValidTurn(x, y)) {
            isValid = true
            hasMoved = moveTo(Math.floor(x + 0.5f.toDouble()).toFloat(), Math.floor(y + 0.5f.toDouble()).toFloat())
            if (hasMoved || forceSound) {
                if (!scene.soundPool.play(scene.soundPool.SOUND_CLICK3, 0.2f, 1.0f)) scene.vibrate(Global.VIBRATE_STONE_SNAP)
            }
            return hasMoved
        }
        for (i in -1..1) for (j in -1..1) {
            if (isValidTurn(x + i, y + j)) {
                isValid = true
                hasMoved = moveTo(floor(0.5f + x + i.toDouble()).toFloat(), floor(0.5f + y + j.toFloat()))
                if (hasMoved) {
                    if (!scene.soundPool.play(scene.soundPool.SOUND_CLICK3, 0.2f, 1.0f)) scene.vibrate(Global.VIBRATE_STONE_SNAP)
                }
                return hasMoved
            }
        }
        isValid = false
        return moveTo(x, y)
    }

    @Synchronized
    override fun handlePointerUp(m: PointF): Boolean {
        val stone = stone
        if (status == Status.DRAGGING && stone != null) {
            fieldPoint.x = m.x
            fieldPoint.y = m.y
            scene.modelToBoard(fieldPoint)
            val x = Math.floor(0.5f + fieldPoint.x + stoneRelX - stone.shape.size / 2.toDouble()).toInt()
            val y = Math.floor(0.5f + fieldPoint.y + stoneRelY - stone.shape.size / 2.toDouble()).toInt()
            fieldPoint.x = x.toFloat()
            fieldPoint.y = y.toFloat()
            scene.boardToUnified(fieldPoint)
            if (!scene.verticalLayout) fieldPoint.y = scene.board.width - fieldPoint.x - 1
            if (fieldPoint.y < -2.0f && hasMoved) {
                scene.wheel.currentStone = stone
                status = Status.IDLE
                this.stone = null
            } else if (canCommit && !hasMoved) {
                val turn = Turn(scene.game.currentPlayer, stone.shape.number, floor(pos.y + 0.5f.toDouble()).toInt(), floor(pos.x + 0.5f.toDouble()).toInt(), orientation)
                if (scene.commitCurrentStone(turn)) {
                    status = Status.IDLE
                    this.stone = null
                    scene.wheel.currentStone = null
                }
            } else if (hasMoved) {
                snap(x.toFloat(), y.toFloat(), false)
            }
        }
        if (status == Status.ROTATING) {
            while (rotateAngle < -45.0f) {
                rotateAngle += 90.0f
                rotateRight()
            }
            while (rotateAngle > 45.0f) {
                rotateAngle -= 90.0f
                rotateLef()
            }
            rotateAngle = 0.0f
            snap(pos.x, pos.y, true)
        }
        if (status == Status.FLIPPING_HORIZONTAL) {
            if (abs(rotateAngle) > 90.0f) mirrorOverY()
            rotateAngle = 0.0f
            snap(pos.x, pos.y, true)
        }
        if (status == Status.FLIPPING_VERTICAL) {
            if (abs(rotateAngle) > 90.0f) mirrorOverX()
            rotateAngle = 0.0f
            snap(pos.x, pos.y, true)
        }
        status = Status.IDLE
        return false
    }

    @Synchronized
    fun stopDragging() {
        stone = null
        currentColor = 0
        status = Status.IDLE
    }

    @Synchronized
    fun startDragging(fieldPoint: PointF?, stone: Stone, orientation: Orientation, color: Int) {
        this.stone = stone
        currentColor = color
        status = Status.DRAGGING
        hasMoved = false
        canCommit = false
        /* TODO: set this to about 3 above the touch event to make stone visible
		 * when started dragging */stoneRelX = 0f
        stoneRelY = 0f
        this.orientation = orientation
        if (fieldPoint != null) {
            val x = floor(0.5f + fieldPoint.x + stoneRelX - stone.shape.size / 2.0).toInt()
            val y = floor(0.5f + fieldPoint.y + stoneRelY - stone.shape.size / 2.0).toInt()
            moveTo(x.toFloat(), y.toFloat())
        }
        isValid = isValidTurn(pos.x, pos.y)
    }

    override fun execute(elapsed: Float): Boolean {
        return false
    }

    companion object {
        private val tag = CurrentStone::class.java.simpleName
        private const val overlay_radius = 6.0f
    }
}