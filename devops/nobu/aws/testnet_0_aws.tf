provider "aws" {
  region     = "us-east-1"
}

variable "product_name" { }
variable "seed_ip_file_name" { }
variable "id_phrases" {}
variable "ssh_key_name" {}

resource "aws_instance" "openstar_testnet_nobu" {
  ami           = "ami-07e3edee2101eb957"
  instance_type = "t2.medium"
  key_name      = "${var.ssh_key_name}"

  security_groups = [
    "${aws_security_group.allow_inbound_nobu.name}",
    "${aws_security_group.allow_outbound_nobu.name}"
  ]

  provisioner "local-exec" {
    command = "echo '${self.public_ip}\\c' > ${var.seed_ip_file_name}"
  }

  connection {
    type          = "ssh"
    user          = "ubuntu"
    private_key   = "${file("${var.pem_file}")}"

  }

  provisioner "remote-exec" {
    inline = ["(sleep 2 && reboot)&"]
  }

  provisioner "file" {
    source      = "../../../sss.openstar-nobu/target/universal/${var.product_name}.zip"
    destination = "~/${var.product_name}.zip"

  }

  provisioner "remote-exec" {
    inline = [
      "unzip ~/${var.product_name}.zip",
      "cd ${var.product_name}",
      "mkdir -p ~/.nobu/keys/",
      "cp ./keys/* ~/.nobu/keys/",
      "tmux new -d -s openstar './bin/nobu ${var.id_phrases}'"
    ]

  }

}

