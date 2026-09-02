package pl.opole.edziennik.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.opole.edziennik.ui.theme.PillShape

/**
 * Ekran pierwszego uruchomienia — appka nie ma wbudowanego na stałe klucza
 * konsumenta USOS (patrz `Config.kt`), więc trzeba go tu wpisać ręcznie,
 * raz, przed pierwszym logowaniem. Zapisany trwale przez `CredentialsStore`
 * — kolejne uruchomienia od razu przechodzą do ekranu logowania.
 */
@Composable
fun SetupScreen(onSaved: (consumerKey: String, consumerSecret: String) -> Unit) {
    var consumerKey by remember { mutableStateOf("") }
    var consumerSecret by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Konfiguracja",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Ta appka wymaga danych dostępowych do USOS API (Consumer Key/" +
                "Secret). Zarejestruj własną aplikację na " +
                "usosapps.po.edu.pl/developers albo wklej dane, które " +
                "dostałeś od kogoś, kto już to zrobił.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = consumerKey,
            onValueChange = { consumerKey = it },
            label = { Text("Consumer Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = consumerSecret,
            onValueChange = { consumerSecret = it },
            label = { Text("Consumer Secret") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onSaved(consumerKey.trim(), consumerSecret.trim()) },
            enabled = consumerKey.isNotBlank() && consumerSecret.isNotBlank(),
            shape = PillShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Zapisz i przejdź dalej", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Dane zostają zapisane tylko lokalnie, na tym telefonie.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
