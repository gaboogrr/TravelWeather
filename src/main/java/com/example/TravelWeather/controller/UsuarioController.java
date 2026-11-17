package com.example.TravelWeather.controller;

import com.example.TravelWeather.model.Rol;
import com.example.TravelWeather.model.Usuario;
import com.example.TravelWeather.repository.UsuarioRepository;
import com.example.TravelWeather.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
public class UsuarioController {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JavaMailSender mailSender;

    // Almacén temporal en memoria para los códigos de verificación (Email -> Código)
    private static final Map<String, String> codigosVerificacion = new HashMap<>();

    // 1. CARGAR TODOS
    @GetMapping
    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    // 2. ENVIAR CÓDIGO REAL AL CORREO (Y CREAR AL ADMIN SI NO EXISTE)
    @PostMapping("/enviar-codigo")
    public ResponseEntity<?> enviarCodigo(@RequestBody Usuario usuarioPeticion) {
        String email = usuarioPeticion.getEmail();
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("El correo es obligatorio.");
        }

        // Verificar si el usuario ya existe por correo, si no, crearlo automáticamente como ADMIN
        Optional<Usuario> usuarioExistente = usuarioRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst();

        if (!usuarioExistente.isPresent()) {
            Usuario nuevoAdmin = new Usuario();
            nuevoAdmin.setNombre("Profesor Admin");
            nuevoAdmin.setEmail(email);
            
            // Asignamos el rol 1 (ADMIN)
            Rol rolAdmin = new Rol();
            rolAdmin.setId(1);
            nuevoAdmin.setRol(rolAdmin);
            
            usuarioRepository.save(nuevoAdmin);
        }

        // Generar código aleatorio de 4 dígitos
        String codigo = String.valueOf((int)(Math.random() * 9000) + 1000);
        codigosVerificacion.put(email, codigo);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Código de Verificación - Examen API REST");
            message.setText("Hola! Tu código de verificación de Administrador es: " + codigo);
            
            mailSender.send(message);
            return ResponseEntity.ok().body("{\"mensaje\": \"Correo enviado exitosamente a " + email + "\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al enviar el correo real: " + e.getMessage());
        }
    }

    // 3. VERIFICAR CÓDIGO INGRESADO
    @PostMapping("/verificar-codigo")
    public ResponseEntity<?> verificarCodigo(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String codigoIngresado = payload.get("codigo");

        if (codigosVerificacion.containsKey(email) && codigosVerificacion.get(email).equals(codigoIngresado)) {
            return ResponseEntity.ok().body("{\"mensaje\": \"Código verificado correctamente.\"}");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Código de verificación incorrecto.");
    }

    // 4. LOGIN (Genera el Token JWT buscando por Correo)
    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@RequestBody Usuario credenciales) {
        Optional<Usuario> usuario = usuarioRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(credenciales.getEmail()))
                .findFirst();
        
        if (usuario.isPresent()) {
            String token = jwtUtil.generarToken(usuario.get().getId());
            return ResponseEntity.ok("{\"token\": \"" + token + "\", \"id\": " + usuario.get().getId() + "}");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales incorrectas.");
    }

// 5. REGISTRO (Devuelve el usuario completo incluyendo el ID generado)
    @PostMapping
    public ResponseEntity<?> registrarUsuario(@RequestBody Usuario nuevoUsuario) {
        // Si no se le envió un rol por defecto, aseguramos que tenga una estructura válida
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);
        return ResponseEntity.ok(usuarioGuardado);
    }

    // 6. ACCESO ARCO (Protegido con JWT)
    @GetMapping("/{idBuscado}")
    public ResponseEntity<?> obtenerUsuario(
            @PathVariable Integer idBuscado, 
            @RequestHeader("Authorization") String authHeader) { 
        try {
            Integer idLogueado = jwtUtil.extraerId(authHeader);
            if (idLogueado == 1 || idBuscado.equals(idLogueado)) {
                return usuarioRepository.findById(idBuscado).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso Denegado.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido.");
        }
    }

    // 7. RECTIFICACIÓN ARCO (Protegido con JWT)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuario(
            @PathVariable Integer id,
            @RequestBody Usuario usuarioActualizado,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer idLogueado = jwtUtil.extraerId(authHeader);
            if (!idLogueado.equals(id)) return ResponseEntity.status(403).body("No puedes editar otras cuentas.");
            
            return usuarioRepository.findById(id).map(usuario -> {
                usuario.setNombre(usuarioActualizado.getNombre());
                usuario.setEmail(usuarioActualizado.getEmail());
                return ResponseEntity.ok(usuarioRepository.save(usuario));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido.");
        }
    }

    // 8. CANCELACIÓN ARCO (Protegido con JWT)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer idLogueado = jwtUtil.extraerId(authHeader);
            if (!idLogueado.equals(id)) return ResponseEntity.status(403).body("No puedes borrar otras cuentas.");
            
            if (!usuarioRepository.existsById(id)) return ResponseEntity.notFound().build();
            usuarioRepository.deleteById(id);
            return ResponseEntity.ok("Cuenta eliminada.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido.");
        }
    }
}