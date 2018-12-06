provider "scaleway" {
  region       = "ams1"
}


module "alice_server"{
  name = "alice"
  source = "../../modules/new_server"
}

module "install_alice" {
  source = "./install_node"
  id_name = "alice"
  host = "${module.alice_server.new_ip}"
}


module "eve_server"{
  name = "eve"
  source = "../../modules/new_server"
}

module "install_eve" {
  source = "./install_node"
  id_name = "eve"
  host = "${module.eve_server.new_ip}"
}


module "bob_server"{
  name = "bob"
  source = "../../modules/new_server"
}

module "install_bob" {
  source = "./install_node"
  id_name = "bob"
  host = "${module.bob_server.new_ip}"
}