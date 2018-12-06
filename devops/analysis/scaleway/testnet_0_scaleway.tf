provider "scaleway" {
  region       = "ams1"
}


module "analysis_server"{
  name = "analysis"
  source = "../../modules/new_server"
}

variable "product_name" { default = "analysis-0.4.0-SNAPSHOT" }
variable "seed_ip_file_name" { default = "openstar_analysis_ip.txt" }

resource "null_resource" "os_testnet_analysis" {

  connection {
    type = "ssh"
    user = "ubuntu"
    agent = true
    host = "${module.analysis_server.new_ip}"
  }

  provisioner "local-exec" {
    command = "echo '${module.analysis_server.new_ip}\\c' > ${var.seed_ip_file_name}"
  }

  provisioner "file" {
    source      = "../../../sss.asado-analysis/target/universal/${var.product_name}.zip"
    destination = "~/${var.product_name}.zip"

  }

  provisioner "remote-exec" {
    inline = [
      "unzip ~/${var.product_name}.zip",
      "cd ${var.product_name}",
      "tmux new -d -s openstar './bin/analysis -J-Djavax.accessibility.assistive_technologies=\" \"'"
    ]

  }
}