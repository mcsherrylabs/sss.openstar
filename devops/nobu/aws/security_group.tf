resource "aws_security_group" "allow_inbound_nobu" {
  name        = "allow-all-nobu"
  description = "Allow inbound traffic"

  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "allow_outbound_nobu" {
  name        = "allow-all-outbound-nobu"
  description = "Allow all outbound traffic"

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
