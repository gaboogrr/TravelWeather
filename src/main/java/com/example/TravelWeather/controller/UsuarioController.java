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
import java.util.concurrent.ConcurrentHashMap;

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

    // Almacén temporal para recuperación de contraseña (Email -> [código, expiraciónMs])
    private static final Map<String, String[]> codigosRecuperacion = new ConcurrentHashMap<>();

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
            nuevoAdmin.setPassword(passwordEncoder.encode("admin123"));

            Rol rolAdmin = new Rol();
            rolAdmin.setId(1);
            nuevoAdmin.setRol(rolAdmin);

            usuarioRepository.save(nuevoAdmin);
        }

        String codigo = String.valueOf((int) (Math.random() * 9000) + 1000);
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

    // 7. RECTIFICACIÓN ARCO (Valida contraseña actual antes de actualizar datos o contraseña)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuario(
            @PathVariable Integer id,
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer idLogueado = jwtUtil.extraerId(authHeader);
            if (!idLogueado.equals(id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes editar otras cuentas."));
            }

            Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Usuario usuario = usuarioOpt.get();
            String passwordActual = payload.get("passwordActual");

            if (passwordActual == null || passwordActual.isBlank()
                    || !passwordEncoder.matches(passwordActual, usuario.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "La contraseña actual es incorrecta."));
            }

            String nuevoNombre = payload.get("nombre");
            String nuevoEmail = payload.get("email");
            String nuevaPwd = payload.get("password");

            if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
                usuario.setNombre(nuevoNombre.trim());
            }
            if (nuevoEmail != null && !nuevoEmail.trim().isEmpty()) {
                String emailLimpio = nuevoEmail.trim();
                if (!emailLimpio.equalsIgnoreCase(usuario.getEmail()) && usuarioRepository.existsByEmail(emailLimpio)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Ese correo electrónico ya está en uso por otra cuenta."));
                }
                usuario.setEmail(emailLimpio);
            }
            if (nuevaPwd != null && !nuevaPwd.trim().isEmpty()) {
                if (nuevaPwd.length() < 6) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "La nueva contraseña debe tener al menos 6 caracteres."));
                }
                usuario.setPassword(passwordEncoder.encode(nuevaPwd));
            }

            return ResponseEntity.ok(usuarioRepository.save(usuario));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token inválido."));
        }
    }

    // 8. CANCELACIÓN ARCO (Valida contraseña actual antes de eliminar la cuenta)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(
            @PathVariable Integer id,
            @RequestBody(required = false) Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer idLogueado = jwtUtil.extraerId(authHeader);
            if (!idLogueado.equals(id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puedes borrar otras cuentas."));
            }

            Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String passwordActual = (payload != null) ? payload.get("passwordActual") : null;
            if (passwordActual == null || passwordActual.isBlank()
                    || !passwordEncoder.matches(passwordActual, usuarioOpt.get().getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "La contraseña actual es incorrecta. No se eliminó la cuenta."));
            }

            usuarioRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("mensaje", "Cuenta eliminada correctamente."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token inválido."));
        }
    }

    // 9. OLVIDÉ MI CONTRASEÑA - PASO 1: ENVIAR CÓDIGO AL CORREO
    @PostMapping("/solicitar-recuperacion")
    public ResponseEntity<?> solicitarRecuperacion(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingresa tu correo electrónico."));
        }

        String emailLimpio = email.trim();
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(emailLimpio);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No existe ninguna cuenta registrada con ese correo."));
        }

        String codigo = String.valueOf((int) (Math.random() * 900000) + 100000);
        long expiraEn = System.currentTimeMillis() + (10 * 60 * 1000); // 10 minutos
        codigosRecuperacion.put(emailLimpio.toLowerCase(), new String[]{codigo, String.valueOf(expiraEn)});

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(emailLimpio);
            message.setSubject("TravelWeather - Código de recuperación de contraseña");
            message.setText("Hola " + usuarioOpt.get().getNombre() + ",\n\n"
                    + "Recibimos una solicitud para restablecer tu contraseña.\n"
                    + "Tu código de verificación es: " + codigo + "\n\n"
                    + "Este código es válido por 10 minutos. Si no solicitaste este cambio, ignora este correo.");

            mailSender.send(message);
            return ResponseEntity.ok(Map.of("mensaje", "Código enviado exitosamente a " + emailLimpio));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al enviar el correo: " + e.getMessage()));
        }
    }

    // 10. OLVIDÉ MI CONTRASEÑA - PASO 2: VERIFICAR CÓDIGO Y RESTABLECER
    @PostMapping("/restablecer-password")
    public ResponseEntity<?> restablecerPassword(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String codigo = payload.get("codigo");
        String nuevaPassword = payload.get("nuevaPassword");

        if (email == null || codigo == null || nuevaPassword == null || nuevaPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Completa todos los campos y usa una contraseña de al menos 6 caracteres."));
        }

        String key = email.trim().toLowerCase();
        String[] datosCodigo = codigosRecuperacion.get(key);

        if (datosCodigo == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No hay ninguna solicitud de recuperación activa para este correo."));
        }

        if (System.currentTimeMillis() > Long.parseLong(datosCodigo[1])) {
            codigosRecuperacion.remove(key);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "El código ha expirado. Solicita uno nuevo."));
        }

        if (!datosCodigo[0].equals(codigo.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "El código de verificación es incorrecto."));
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email.trim());
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado."));
        }

        Usuario usuario = usuarioOpt.get();
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);
        codigosRecuperacion.remove(key);

        return ResponseEntity.ok(Map.of("mensaje", "Contraseña restablecida correctamente."));
    }
}