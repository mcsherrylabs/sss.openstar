
variable "host" {}
variable "product_name" { default = "sss-asado-node-0.4.0-SNAPSHOT"}
variable "id_phrase" { default = "password" }
variable "id_name" { }

resource "null_resource" "openstar_testnet" {

  connection {
    type = "ssh"
    user = "ubuntu"
    agent = true
    host = "${var.host}"
  }

  provisioner "file" {
    source = "../../../sss.asado-node/target/universal/${var.product_name}.zip"
    destination = "~/${var.product_name}.zip"
  }

  provisioner "remote-exec" {
    inline = [
      "unzip ~/${var.product_name}.zip",
      "cd ${var.product_name}",
      "tmux new -d -s openstar './bin/service_node ${var.id_name} ${var.id_phrase}'"
    ]
  }
}