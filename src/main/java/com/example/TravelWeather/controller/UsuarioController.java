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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

        Optional<Usuario> usuarioExistente = usuarioRepository.findByEmail(email);

        if (!usuarioExistente.isPresent()) {
            Usuario nuevoAdmin = new Usuario();
            nuevoAdmin.setNombre("Profesor Admin");
            nuevoAdmin.setEmail(email);
            // Contraseña por defecto encriptada para cumplir con NOT NULL en PostgreSQL
            nuevoAdmin.setPassword(passwordEncoder.encode("admin123"));
            
            Rol rolAdmin = new Rol();
            rolAdmin.setId(1);
            nuevoAdmin.setRol(rolAdmin);
            
            usuarioRepository.save(nuevoAdmin);
        }

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

    // 4. LOGIN (Valida Correo + Contraseña encriptada y genera el Token JWT con el ID)
    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(@RequestBody Usuario credenciales) {
        if (credenciales.getEmail() == null || credenciales.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingresa tu correo y contraseña."));
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(credenciales.getEmail());
        
        if (usuarioOpt.isPresent() && passwordEncoder.matches(credenciales.getPassword(), usuarioOpt.get().getPassword())) {
            Usuario usuario = usuarioOpt.get();
            String token = jwtUtil.generarToken(usuario.getId());
            return ResponseEntity.ok(Map.of(
                "token", token,
                "id", usuario.getId()
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Correo o contraseña incorrectos."));
    }

    // 5. REGISTRO (Encripta la contraseña antes de guardar)
    @PostMapping
    public ResponseEntity<?> registrarUsuario(@RequestBody Usuario nuevoUsuario) {
        if (nuevoUsuario.getEmail() == null || nuevoUsuario.getPassword() == null || nuevoUsuario.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres."));
        }
        if (usuarioRepository.existsByEmail(nuevoUsuario.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Este correo ya está registrado."));
        }

        if (nuevoUsuario.getRol() == null) {
            Rol rolUsuario = new Rol();
            rolUsuario.setId(2);
            nuevoUsuario.setRol(rolUsuario);
        }

        nuevoUsuario.setPassword(passwordEncoder.encode(nuevoUsuario.getPassword()));
        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioGuardado);
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

    // 7. RECTIFICACIÓN ARCO (Protegido con JWT - Actualiza datos y contraseña opcional)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuario(
            @PathVariable Integer id,
            @RequestBody Usuario usuarioActualizado,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer idLogueado = jwtUtil.extraerId(authHeader);
            if (!idLogueado.equals(id)) {
                return ResponseEntity.status(403).body(Map.of("error", "No puedes editar otras cuentas."));
            }
            
            return usuarioRepository.findById(id).map(usuario -> {
                usuario.setNombre(usuarioActualizado.getNombre());
                usuario.setEmail(usuarioActualizado.getEmail());

                String nuevaPwd = usuarioActualizado.getPassword();
                if (nuevaPwd != null && !nuevaPwd.trim().isEmpty()) {
                    if (nuevaPwd.length() < 6) {
                        return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña debe tener al menos 6 caracteres."));
                    }
                    usuario.setPassword(passwordEncoder.encode(nuevaPwd));
                }

                return ResponseEntity.ok(usuarioRepository.save(usuario));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token inválido."));
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