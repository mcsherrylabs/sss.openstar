

provider "aws" {
  region     = "us-east-1"
}

variable "product_name" {}
variable "pem_file" { }
variable "seed_ip_file_name" {}
variable "quorum_ids" { type = "list"}
variable "id_phrases" {}
variable "ssh_key_name" {}
variable "quorum_ports" { type = "list"}

resource "aws_instance" "openstar_testnet" {
  ami           = "ami-07e3edee2101eb957"
  instance_type = "t2.medium"
  count = 3
  key_name      = "${var.ssh_key_name}"

  security_groups = [
    "${aws_security_group.allow_inbound.name}",
    "${aws_security_group.allow_outbound.name}"
  ]

  provisioner "local-exec" {
    command = "echo '${var.quorum_ids[count.index]}:${self.public_ip}:${var.quorum_ports[count.index]}::\\c' >> ${var.seed_ip_file_name}"
  }

}


resource "null_resource" "openstar_testnet" {

  count = 3

  # Changes to any instance of the cluster requires re-provisioning
  triggers {
    openstar_testnet_instance_ids = "${join(",", aws_instance.openstar_testnet.*.id)}"
  }

  provisioner "local-exec" {
    command = "cp2openstar"
  }

  connection {
    type          = "ssh"
    user          = "ubuntu"
    private_key   = "${file("${var.pem_file}")}"
    host          = "${aws_instance.openstar_testnet.*.public_ip[count.index]}"
  }

  provisioner "remote-exec" {
    inline = ["(sleep 2 && reboot)&"]
  }

  provisioner "file" {
    source      = "../../sss.asado-node/target/universal/${var.product_name}.zip"
    destination = "~/${var.product_name}.zip"

  }

  provisioner "remote-exec" {
    inline = [
      "unzip ~/${var.product_name}.zip",
      "cd ${var.product_name}",
      "tmux new -d -s openstar './bin/service_node ${var.quorum_ids[count.index]} ${var.id_phrases}'"
    ]

  }
}