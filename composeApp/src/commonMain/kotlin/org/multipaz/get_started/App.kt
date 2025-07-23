package org.multipaz.get_started

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import multipazgettingstartedsample.composeapp.generated.resources.Res
import multipazgettingstartedsample.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Simple
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.compose.qrcode.generateQrCode
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.DocumentStore
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.models.presentment.SimplePresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.storage.Storage
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Platform
import org.multipaz.util.UUID
import org.multipaz.util.toBase64Url
import kotlin.time.Duration.Companion.days

// Storage
lateinit var storage: Storage
lateinit var secureArea: SecureArea
lateinit var secureAreaRepository: SecureAreaRepository

// DocumentStore
lateinit var documentTypeRepository: DocumentTypeRepository
lateinit var documentStore: DocumentStore

lateinit var presentmentModel: PresentmentModel
lateinit var presentmentSource: PresentmentSource

lateinit var readerTrustManager: TrustManager

@Composable
@Preview
fun App(promptModel: PromptModel) {
    MaterialTheme {
        // This ensures all prompts inherit the app's main style
        PromptDialogs(promptModel)
        // ... rest of your UI

        LaunchedEffect(null) {

            // Storage
            storage = Platform.getNonBackedUpStorage()
            secureArea = Platform.getSecureArea(storage)
            secureAreaRepository = SecureAreaRepository.Builder().add(secureArea).build()

            // DocumentStore
            documentTypeRepository = DocumentTypeRepository().apply {
                addDocumentType(DrivingLicense.getDocumentType())
            }
            documentStore = buildDocumentStore(
                storage = storage,
                secureAreaRepository = secureAreaRepository
            ) {}

            // Creation of an mDoc
            if (documentStore.listDocuments().isEmpty()) {

                // Creating a Document
                val document = documentStore.createDocument(
                    displayName = "Erika's Driving License",
                    typeDisplayName = "Utopia Driving License",
                )

                // Creating an MdocCredential

                // 1. Prepare Timestamps
                val now = Clock.System.now()
                val signedAt = now
                val validFrom = now
                val validUntil = now + 365.days

                // 2. Generate IACA Certificate
                val iacaKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val iacaCert = X509Cert.fromPem(
                    """
                        -----BEGIN CERTIFICATE-----
                        MIICZDCCAemgAwIBAgIQ+NBW8+WP8e2c4/wnqmyywzAKBggqhkjOPQQDAzAuMQswCQYDVQQGDAJV
                        UzEfMB0GA1UEAwwWT1dGIE11bHRpcGF6IFRFU1QgSUFDQTAeFw0yNTA3MjMwNTUzNDRaFw0zMDA3
                        MjMwNTUzNDRaMC4xCzAJBgNVBAYMAlVTMR8wHQYDVQQDDBZPV0YgTXVsdGlwYXogVEVTVCBJQUNB
                        MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEAHbz7/IxXtIS7b0KILAa3ul6FHbXmZzZrIytukNKaFZ1
                        lCu8d2Pg1gysnoHRoYuompaifw5lqP9BDNajGOTcTuZIq5K9BAQKJzrrtj0tsFO6LOOHR2IOs7bA
                        cIN9KQ3Vo4HLMIHIMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMC0GA1UdEgQm
                        MCSGImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tL3dlYnNpdGUwMwYDVR0fBCwwKjAooCagJIYi
                        aHR0cHM6Ly9pc3N1ZXIuZXhhbXBsZS5jb20vY3JsLmNybDAdBgNVHQ4EFgQUBE7IHeNuw2JjaSRD
                        qoywl2HkVokwHwYDVR0jBBgwFoAUBE7IHeNuw2JjaSRDqoywl2HkVokwCgYIKoZIzj0EAwMDaQAw
                        ZgIxAM4OydScN5KcUlrj6YLXAvCnQ4vv5fW1PqlcgH35cq7PElqMrRXlFFpfAwhEj50LjwIxAMfX
                        EN8aZaMawjzmxiTv5hRVZMud/XjBDjCe50EMiYxKfWKdzfBkqhCkVrGdtGmUDg==
                        -----END CERTIFICATE-----
                    """.trimIndent()
                )

                // 3. Generate Document Signing (DS) Certificate
                val dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val dsCert = MdocUtil.generateDsCertificate(
                    iacaCert = iacaCert,
                    iacaKey = iacaKey,
                    dsKey = dsKey.publicKey,
                    subject = X500Name.fromName(name = "CN=Test DS Key"),
                    serial = ASN1Integer.fromRandom(numBits = 128),
                    validFrom = validFrom,
                    validUntil = validUntil
                )

                // 4. Create the mDoc Credential
                DrivingLicense.getDocumentType().createMdocCredentialWithSampleData(
                    document = document,
                    secureArea = secureArea,
                    createKeySettings = CreateKeySettings(
                        algorithm = Algorithm.ESP256,
                        nonce = "Challenge".encodeToByteString(),
                        userAuthenticationRequired = true
                    ),
                    dsKey = dsKey,
                    dsCertChain = X509CertChain(listOf(dsCert)),
                    signedAt = signedAt,
                    validFrom = validFrom,
                    validUntil = validUntil,
                )
            }

            // todo: add button to list docs
            // todo: add button for deletion

            presentmentModel = PresentmentModel().apply { setPromptModel(promptModel) }
            readerTrustManager = TrustManager().apply {
                addTrustPoint(
                    TrustPoint(
                        certificate = X509Cert.fromPem(
                            """
                                -----BEGIN CERTIFICATE-----
                                MIICUTCCAdegAwIBAgIQppKZHI1iPN290JKEA79OpzAKBggqhkjOPQQDAzArMSkwJwYDVQQDDCBP
                                V0YgTXVsdGlwYXogVGVzdEFwcCBSZWFkZXIgUm9vdDAeFw0yNDEyMDEwMDAwMDBaFw0zNDEyMDEw
                                MDAwMDBaMCsxKTAnBgNVBAMMIE9XRiBNdWx0aXBheiBUZXN0QXBwIFJlYWRlciBSb290MHYwEAYH
                                KoZIzj0CAQYFK4EEACIDYgAE+QDye70m2O0llPXMjVjxVZz3m5k6agT+wih+L79b7jyqUl99sbeU
                                npxaLD+cmB3HK3twkA7fmVJSobBc+9CDhkh3mx6n+YoH5RulaSWThWBfMyRjsfVODkosHLCDnbPV
                                o4G/MIG8MA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMFYGA1UdHwRPME0wS6BJ
                                oEeGRWh0dHBzOi8vZ2l0aHViLmNvbS9vcGVud2FsbGV0LWZvdW5kYXRpb24tbGFicy9pZGVudGl0
                                eS1jcmVkZW50aWFsL2NybDAdBgNVHQ4EFgQUq2Ub4FbCkFPx3X9s5Ie+aN5gyfUwHwYDVR0jBBgw
                                FoAUq2Ub4FbCkFPx3X9s5Ie+aN5gyfUwCgYIKoZIzj0EAwMDaAAwZQIxANN9WUvI1xtZQmAKS4/D
                                ZVwofqLNRZL/co94Owi1XH5LgyiBpS3E8xSxE9SDNlVVhgIwKtXNBEBHNA7FKeAxKAzu4+MUf4gz
                                8jvyFaE0EUVlS2F5tARYQkU6udFePucVdloi
                                -----END CERTIFICATE-----
                            """.trimIndent().trim()
                        ),
                        displayName = "OWF Multipaz TestApp",
                        displayIcon = null,
                        privacyPolicyUrl = "https://apps.multipaz.org"
                    )
                )
            }
            presentmentSource = SimplePresentmentSource(
                documentStore = documentStore,
                documentTypeRepository = documentTypeRepository,
                readerTrustManager = readerTrustManager,
                preferSignatureToKeyAgreement = true,
                domainMdocSignature = "mdoc",
            )
        }

        val blePermissionState = rememberBluetoothPermissionState()
        val coroutineScope = rememberCoroutineScope { promptModel }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Bluetooth Permission
            if (!blePermissionState.isGranted) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            blePermissionState.launchPermissionRequest()
                        }
                    }
                ) {
                    Text("Request BLE permissions")
                }
            } else {
                val deviceEngagement = remember { mutableStateOf<ByteString?>(null) }
                val state = presentmentModel.state.collectAsState()
                when (state.value) {
                    PresentmentModel.State.IDLE -> {
                        showQrButton(deviceEngagement)
                    }

                    PresentmentModel.State.CONNECTING -> {
                        showQrCode(deviceEngagement)
                    }

                    PresentmentModel.State.WAITING_FOR_SOURCE,
                    PresentmentModel.State.PROCESSING,
                    PresentmentModel.State.WAITING_FOR_DOCUMENT_SELECTION,
                    PresentmentModel.State.WAITING_FOR_CONSENT,
                    PresentmentModel.State.COMPLETED -> {
                        Presentment(
                            appName = "Multipaz Getting Started Sample",
                            appIconPainter = painterResource(Res.drawable.compose_multiplatform),
                            presentmentModel = presentmentModel,
                            presentmentSource = presentmentSource,
                            documentTypeRepository = documentTypeRepository,
                            onPresentmentComplete = {
                                presentmentModel.reset()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun showQrButton(showQrCode: MutableState<ByteString?>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            presentmentModel.reset()
            presentmentModel.setConnecting()
            presentmentModel.presentmentScope.launch() {
                val connectionMethods = listOf(
                    MdocConnectionMethodBle(
                        supportsPeripheralServerMode = false,
                        supportsCentralClientMode = true,
                        peripheralServerModeUuid = null,
                        centralClientModeUuid = UUID.randomUUID(),
                    )
                )
                val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val advertisedTransports = connectionMethods.advertise(
                    role = MdocRole.MDOC,
                    transportFactory = MdocTransportFactory.Default,
                    options = MdocTransportOptions(bleUseL2CAP = true),
                )
                val engagementGenerator = EngagementGenerator(
                    eSenderKey = eDeviceKey.publicKey,
                    version = "1.0"
                )
                engagementGenerator.addConnectionMethods(advertisedTransports.map {
                    it.connectionMethod
                })
                val encodedDeviceEngagement = ByteString(engagementGenerator.generate())
                showQrCode.value = encodedDeviceEngagement
                val transport = advertisedTransports.waitForConnection(
                    eSenderKey = eDeviceKey.publicKey,
                    coroutineScope = presentmentModel.presentmentScope
                )
                presentmentModel.setMechanism(
                    MdocPresentmentMechanism(
                        transport = transport,
                        eDeviceKey = eDeviceKey,
                        encodedDeviceEngagement = encodedDeviceEngagement,
                        handover = Simple.NULL,
                        engagementDuration = null,
                        allowMultipleRequests = false
                    )
                )
                showQrCode.value = null
            }
        }) {
            Text("Present mDL via QR")
        }
    }
}

@Composable
private fun showQrCode(deviceEngagement: MutableState<ByteString?>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (deviceEngagement.value != null) {
            val mdocUrl = "mdoc:" + deviceEngagement.value!!.toByteArray().toBase64Url()
            val qrCodeBitmap = remember { generateQrCode(mdocUrl) }
            Text(text = "Present QR code to mdoc reader")
            Image(
                modifier = Modifier.fillMaxWidth(),
                bitmap = qrCodeBitmap,
                contentDescription = null,
                contentScale = ContentScale.FillWidth
            )
            Button(
                onClick = {
                    presentmentModel.reset()
                }
            ) {
                Text("Cancel")
            }
        }
    }
}