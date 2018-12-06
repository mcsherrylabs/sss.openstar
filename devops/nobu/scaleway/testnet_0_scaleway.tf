provider "scaleway" {
  region       = "ams1"
}

module "nobu_server"{
  name = "nobu"
  source = "../../modules/new_server"
}

variable "product_name" { default = "nobu-0.4.0-SNAPSHOT" }
variable "seed_ip_file_name" { default = "openstar_nobu_ip.txt" }
variable "id_phrase" {}

resource "null_resource" "os_testnet_nobu" {

  connection {
    type = "ssh"
    user = "ubuntu"
    agent = true
    host = "${module.nobu_server.new_ip}"
  }

  provisioner "local-exec" {
    command = "echo '${module.nobu_server.new_ip}\\c' > ${var.seed_ip_file_name}"
  }


  provisioner "file" {
    source      = "../../../sss.asado-nobu/target/universal/${var.product_name}.zip"
    destination = "~/${var.product_name}.zip"
  }

  provisioner "remote-exec" {
    inline = [
      "unzip ~/${var.product_name}.zip",
      "cd ${var.product_name}",
      "mkdir -p ~/.nobu/keys/",
      "cp ./keys/* ~/.nobu/keys/",
      "tmux new -d -s openstar './bin/nobu ${var.id_phrase}'"
    ]

  }
}
