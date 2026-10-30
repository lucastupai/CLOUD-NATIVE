package cl.duoc.vidasalud.rabbitadmin.service;

import cl.duoc.vidasalud.rabbitadmin.dto.BindingRequest;
import cl.duoc.vidasalud.rabbitadmin.dto.ExchangeRequest;
import cl.duoc.vidasalud.rabbitadmin.dto.QueueRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {
    @Mock AmqpAdmin amqpAdmin;
    @InjectMocks RabbitAdminService service;

    @Test
    void createsAndDeletesQueue() {
        service.createQueue(new QueueRequest("vidasalud.test.queue", true));
        verify(amqpAdmin).declareQueue(any(Queue.class));

        service.deleteQueue("vidasalud.test.queue");
        verify(amqpAdmin).deleteQueue("vidasalud.test.queue");
    }

    @Test
    void createsDirectExchangeAndBinding() {
        service.createExchange(new ExchangeRequest("vidasalud.test.exchange", "direct"));
        verify(amqpAdmin).declareExchange(any(Exchange.class));

        service.createBinding(new BindingRequest("vidasalud.test.exchange", "vidasalud.test.queue", "test.created"));
        verify(amqpAdmin).declareBinding(any(Binding.class));
    }

    @Test
    void rejectsInvalidExchangeType() {
        assertThatThrownBy(() -> service.createExchange(new ExchangeRequest("vidasalud.test.exchange", "invalid")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
