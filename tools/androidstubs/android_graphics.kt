@file:Suppress("unused", "UNUSED_PARAMETER")
package android.graphics

class Typeface { companion object { const val BOLD = 1 } }
class Bitmap { enum class Config { ARGB_8888 }
  companion object { @JvmStatic fun createBitmap(w: Int, h: Int, c: Config): Bitmap = Bitmap() } }
class Canvas(b: Bitmap) { fun drawPath(p: Path, paint: Paint) {} }
open class Shader { enum class TileMode { CLAMP } }
class LinearGradient(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, m: Shader.TileMode) : Shader()
class Path() { constructor(p: Path) : this()
  fun moveTo(x: Float, y: Float) {}; fun lineTo(x: Float, y: Float) {}; fun close() {} }
class Paint(flags: Int) {
  enum class Style { FILL, STROKE }; enum class Join { ROUND }; enum class Cap { ROUND }
  var style: Style = Style.FILL; var shader: Shader? = null; var strokeWidth: Float = 0f
  var strokeJoin: Join = Join.ROUND; var strokeCap: Cap = Cap.ROUND; var color: Int = 0
  companion object { const val ANTI_ALIAS_FLAG = 1 }
}
object Color { const val TRANSPARENT = 0
  @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int = 0
  @JvmStatic fun red(c: Int): Int = 0; @JvmStatic fun green(c: Int): Int = 0; @JvmStatic fun blue(c: Int): Int = 0 }

