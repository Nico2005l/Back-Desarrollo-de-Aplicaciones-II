# Planta Libre — Manual de usuario

**Versión 1 · 3.er checkpoint (20/11/2026)**

Planta Libre sirve para reservar espacios de trabajo (cubículos y salas) en los edificios de las empresas adheridas. Este manual explica cada tarea paso a paso. La sección 1 es para clientes y la sección 2 para administradores.

> Para levantar el sistema en una máquina local, ver el [README](../README.md). La aplicación queda en http://localhost:8088.

---

## 1. Clientes

### 1.1 Crear una cuenta

1. En la pantalla de ingreso, elegir **Crear cuenta**.
2. Completar nombre, email, teléfono y contraseña. La contraseña tiene que tener entre 8 y 100 caracteres, no ser una contraseña común y no aparecer en filtraciones conocidas.
3. Opcional: agregar un método de pago en el mismo formulario.
4. Elegir **Crear cuenta**. La pantalla pasa a **Confirmá tu email**.
5. Abrir el mail que llega a la casilla y seguir el link. La cuenta queda activa y se ingresa directamente.

Si el mail no llegó, en la pantalla de ingreso aparece la opción **Reenviar link** al intentar entrar con una cuenta sin confirmar.

### 1.2 Ingresar y salir

1. Escribir el email y la contraseña y elegir **Ingresar**.
2. Para salir: abrir el menú de la cuenta, arriba a la derecha, y elegir **Cerrar sesión**.

Después de varios intentos fallidos seguidos, el ingreso se bloquea por unos minutos. El mensaje en pantalla indica cuándo volver a probar.

### 1.3 Recuperar la contraseña

1. En la pantalla de ingreso, elegir **¿Olvidaste tu contraseña?**.
2. Escribir el email de la cuenta y enviar.
3. Seguir el link del mail y elegir una contraseña nueva. Las sesiones abiertas en otros dispositivos se cierran.

### 1.4 Agregar o cambiar un método de pago

Para reservar hace falta al menos una tarjeta.

1. Abrir el menú de la cuenta y elegir **Mi perfil**.
2. En **Métodos de pago**, elegir **Agregar método de pago**.
3. Completar titular, número, vencimiento (MM/AA) y código de seguridad, y elegir **Completar**.

Se pueden guardar hasta 3 tarjetas. Del número solo se guardan los últimos 4 dígitos.

- **Elegir la tarjeta que se usa por defecto:** **Hacer principal**.
- **Cambiar titular o vencimiento:** **Editar**.

### 1.5 Reservar un espacio

1. En el menú, elegir **Empresas** y después la empresa, el edificio y el piso.
2. En el plano del piso, elegir la fecha y el horario. Con los filtros se puede pedir un tipo de espacio o una capacidad mínima.
3. El plano marca los espacios libres, ocupados y bloqueados en esa franja. Elegir un espacio libre, en el plano o en la lista.
4. En el panel del espacio, revisar la semana y elegir la franja.
5. En **Confirmar reserva**, revisar los datos, elegir el método de pago y confirmar.
6. La reserva aparece en **Mis reservas** como *Pendiente* mientras se procesa el pago y pasa a *Confirmada* en unos segundos, sin recargar la página. Llega un mail de confirmación con el monto cobrado.

**Si la tarjeta es rechazada:** la reserva pasa a *Cancelada*, el horario queda libre y llega un mail con el motivo.

**Si alguien tomó la franja mientras se confirmaba:** aparece **Esa franja ya no está disponible** con horarios alternativos. Se puede elegir uno, **Elegir otro espacio** o **Actualizar el calendario**.

### 1.6 Ver y cancelar mis reservas

1. En el menú, elegir **Mis reservas**. Las próximas y el historial están en pestañas separadas.
2. **Ver detalle** muestra el espacio, el horario, el monto, el historial de cambios y cuánto se devolvería si se cancela ahora.
3. Para cancelar, elegir el botón de cancelar de la reserva y confirmar. El reintegro depende de la anticipación, según la política de cancelación que se muestra en el detalle.

Si la pasarela de pagos no responde al pedir el reintegro, aparece el mensaje "La pasarela de pagos no respondió. Tu reserva no cambió; probá de nuevo en unos minutos." La reserva sigue activa y no se cobró ni devolvió nada.

### 1.7 Cambiar el teléfono o la contraseña

En **Mi perfil**, en **Datos personales**, editar el teléfono o cambiar la contraseña y elegir **Guardar**.

Si los datos se cambiaron en otra pestaña o en otro dispositivo después de abrir el perfil, aparece "Alguien modificó estos datos después de que los cargaste. Volvé a cargarlos y repetí el cambio." En ese caso, recargar la página y repetir el cambio.

---

## 2. Administradores

### 2.1 Ingresar con segundo factor

Las cuentas de administrador piden un código de una aplicación de autenticación (Google Authenticator, Microsoft Authenticator u otra compatible con TOTP).

1. Ingresar email y contraseña.
2. **La primera vez:** cargar en la aplicación la clave que muestra la pantalla. Se muestra una sola vez.
3. Escribir el código de 6 dígitos de la aplicación y elegir **Verificar e ingresar**.

### 2.2 Ver la agenda del día

1. En el menú, elegir **Agenda del día**.
2. Elegir empresa, edificio y piso. La agenda muestra cada espacio con sus reservas y bloqueos del día.
3. Con las flechas se pasa al día hábil anterior o siguiente.

### 2.3 Buscar reservas

1. En el menú, elegir **Reservas**.
2. Filtrar por edificio, piso, tipo de espacio, fechas, estado, o escribir el cliente o el código de la reserva (por ejemplo `R-42`).
3. Desde cada fila se puede ver el detalle o cancelar la reserva.

### 2.4 Crear una reserva para un cliente

1. En **Reservas** o en el plano del piso, elegir la opción de nueva reserva.
2. Buscar al cliente por nombre o email y completar espacio, día y horario. A la derecha se ve la ocupación del espacio ese día.
3. Guardar. La reserva queda a nombre del cliente.

### 2.5 Editar una reserva

1. Abrir la reserva desde **Reservas** o desde el plano.
2. Cambiar cliente, espacio, horario o estado. El sistema pide un motivo, que queda en el historial de cambios de la reserva y le llega al cliente por mail.
3. Guardar.

Algunos cambios de estado no se permiten; por ejemplo, una reserva finalizada no cambia. Si el cambio no es válido, el sistema lo indica.

Si otro administrador modificó la misma reserva después de abrirla, aparece "Alguien modificó estos datos después de que los cargaste…". En ese caso, recargarla y repetir el cambio.

### 2.6 Bloquear espacios

Sirve para mantenimiento, eventos u otros motivos: un espacio bloqueado no se puede reservar en ese período.

1. En el menú, elegir **Bloqueos** y después **Crear bloqueo**.
2. Elegir los espacios, el período y el motivo, con un detalle opcional. La vista previa muestra cómo queda en la agenda y cómo lo ve el cliente en el plano.
3. Si hay reservas en ese período, el sistema las lista antes de confirmar. Al confirmar se cancelan y cada cliente recibe un mail con el motivo y el reintegro.

Para quitar un bloqueo, elegir quitarlo desde **Bloqueos**. Si el bloqueo era de varios espacios, se puede quitar uno solo o todo el grupo.

---

## 3. Mensajes frecuentes

| Mensaje | Qué significa | Qué hacer |
|---|---|---|
| Esa franja ya no está disponible | Otra persona reservó ese horario mientras se confirmaba | Elegir una alternativa de las que ofrece la pantalla |
| La pasarela de pagos no respondió… | El procesador de pagos está demorado. No se aplicó ningún cambio | Volver a intentar en unos minutos |
| Alguien modificó estos datos después de que los cargaste… | Otra persona u otra pestaña cambió los mismos datos | Recargar la página y repetir el cambio |
| No tenés permiso para esta acción | La cuenta no tiene el rol necesario | Ingresar con una cuenta de administrador |
| Demasiadas solicitudes | Se hicieron muchos pedidos seguidos | Esperar el tiempo indicado y volver a intentar |
