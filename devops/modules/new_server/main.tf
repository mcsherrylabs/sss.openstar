variable "name" {}

resource "scaleway_server" "openstar_node" {
  name = "os_${var.name}"
  image = "50c2e86a-41f0-4d5b-81ff-2a2636ca30b1"
  type = "START1-S"
  dynamic_ip_required = true
}

output "new_ip" {
  value = "${scaleway_server.openstar_node.public_ip}"
}
