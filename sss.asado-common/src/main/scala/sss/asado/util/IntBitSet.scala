package sss.asado.util

case class IntBitSet(value: Int) {
  def set(pos: Short): IntBitSet = {
    require(pos >= 0 && pos <= 31, s"Can only set bits in range 0 to 31, you tried position $pos")
    IntBitSet(value | (1 << pos))
  }
  def get(pos: Short): Boolean = {
    require(pos >= 0 && pos <= 31, s"Can only get bits in range 0 to 31, you tried position $pos")
    (value & (1L << pos)) != 0
  }

}

