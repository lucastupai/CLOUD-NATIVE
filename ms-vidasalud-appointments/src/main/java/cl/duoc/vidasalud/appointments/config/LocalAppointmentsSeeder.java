package cl.duoc.vidasalud.appointments.config;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;
import cl.duoc.vidasalud.appointments.repository.AppointmentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.OffsetDateTime;

@Configuration
@Profile("local")
public class LocalAppointmentsSeeder {

    @Bean
    CommandLineRunner seedAppointments(AppointmentRepository repository) {
        return args -> {
            if (repository.count() > 0) return;

            Appointment app1 = new Appointment();
            app1.setPatientName("Carlos Muñoz");
            app1.setPatientEmail("carlos.munoz@duocuc.cl");
            app1.setPatientId("demo-patient-1");
            app1.setServiceId(1L);
            app1.setBoxId(1L);
            app1.setStatus(AppointmentStatus.CONFIRMADA);
            app1.setScheduledAt(OffsetDateTime.now().plusHours(2));
            repository.save(app1);

            Appointment app2 = new Appointment();
            app2.setPatientName("Camila Fernández");
            app2.setPatientEmail("camila.fernandez@duocuc.cl");
            app2.setPatientId("demo-patient-2");
            app2.setServiceId(2L);
            app2.setStatus(AppointmentStatus.SOLICITADA);
            app2.setScheduledAt(OffsetDateTime.now().plusHours(4));
            repository.save(app2);

            Appointment app3 = new Appointment();
            app3.setPatientName("Rodrigo Morales");
            app3.setPatientEmail("rodrigo.morales@duocuc.cl");
            app3.setPatientId("demo-patient-3");
            app3.setServiceId(1L);
            app3.setBoxId(1L);
            app3.setStatus(AppointmentStatus.EN_ESPERA);
            app3.setScheduledAt(OffsetDateTime.now().minusMinutes(30));
            repository.save(app3);
        };
    }
}
