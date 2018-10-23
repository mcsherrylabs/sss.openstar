

provider "aws" {
  region     = "us-east-1"
}

variable "product_name" { default = "nobu-0.4.0-SNAPSHOT" }
variable "pem_file" { default = "~/.ssh/testnet_0.pem" }
variable "seed_ip_file_name" { default = "openstar_seed_ip.txt"}

resource "aws_instance" "openstar_testnet" {
  ami           = "ami-0ac14390942cce323"
  instance_type = "t2.medium"
  count = 3
  key_name      = "testnet_0"

  security_groups = [
    "${aws_security_group.allow_ssh.name}",
    "${aws_security_group.allow_outbound.name}"
  ]

  provisioner "local-exec" {
    command = "echo ${self.public_ip}:: >> ${var.seed_ip_file_name}"
  }

  provisioner "file" {
    source      = "../sss.asado-nobu/target/universal/${var.product_name}.zip"
    destination = "~/${var.product_name}.zip"

    connection {
      type          = "ssh"
      user          = "ubuntu"
      private_key   = "${file("${var.pem_file}")}"
    }
  }

  provisioner "remote-exec" {
    inline = [
      "unzip ~/${var.product_name}.zip",
      "cd ${var.product_name}",
      "tmux new -d -s openstar './bin/nobu'"
    ]

    connection {
      type          = "ssh"
      user          = "ubuntu"
      private_key   = "${file("${var.pem_file}")}"
    }
  }

}
