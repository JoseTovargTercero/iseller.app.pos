package com.iseller.pos;

public class Usuario {
    int id;
    String Usuario, nombre, telefono, contrasena;

    public Usuario() {

    }

    public Usuario(String Usuario, String nombre, String telefono, String contrasena) {
        this.Usuario = Usuario;
        this.nombre = nombre;
        this.telefono = telefono;
        this.contrasena = contrasena;
    }

    public boolean isNull(){
        if(Usuario.equals("") || nombre.equals("") || telefono.equals("") || contrasena.equals("")){
            return false;
        }else{
            return true;
        }
    }

    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + id +
                ", Usuario='" + Usuario + '\'' +
                ", nombre='" + nombre + '\'' +
                ", telefono='" + telefono + '\'' +
                ", contrasena='" + contrasena + '\'' +
                '}';
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsuario() {
        return Usuario;
    }
    public void setUsuario(String usuario) {
        this.Usuario = usuario;
    }
    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }
}
