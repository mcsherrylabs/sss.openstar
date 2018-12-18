package sss.openstar.chains

trait BlockChainSettings {
  val inflationRatePerBlock: Int
  val maxTxPerBlock: Int
  val maxBlockOpenSecs: Int
  val maxSignatures: Int
  val spendDelayBlocks: Int
  val numTxWriters: Int
  val numBlocksCached: Int
}
