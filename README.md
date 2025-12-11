# Post Service - Dokumentacija

## Pregled
Mikroservis za upravljanje objav (postov) digitalnih datotek, ki omogoča nalaganje, iskanje, ocenjevanje in nakup digitalnih izdelkov.

## Entitete

### Posts
Glavna entiteta za digitalne objave/izdelke.

**Polja:**
- `id` (Long) - primarni ključ
- `title` (String) - naslov objave
- `sellerId` (String) - ID prodajalca (iz Clerk/User service)
- `description` (TEXT) - opis izdelka
- `tags` (Set<Tag>) - oznake (ART, MUSIC, VIDEO, CODE, TEMPLATE, PHOTO, MODEL_3D, FONT, OTHER)
- `azBlobName` (String) - ime datoteke v Azure Blob Storage (unique)
- `fileVersion` (Integer) - verzija datoteke (privzeto 1, narašča ob posodobitvi)
- `previewImages` (List<String>) - seznam imen preview slik
- `buyers` (Set<String>) - ID-ji kupcev, ki so kupili izdelek
- `copies` (Integer) - število razpoložljivih kopij (-1 = neomejeno, 0 = razprodano, >0 = število kopij)
- `priceUSD` (Double) - cena v USD
- `uploadTime` (LocalDateTime) - čas prvotnega nalaganja
- `lastTimeModified` (LocalDateTime) - čas zadnje spremembe
- `status` (Status) - status objave

**Status enum:**
- `ACTIVE` - aktiven post, viden javno
- `DISABLED` - onemogočen, ni viden javno
- `USER_DELETED` - izbrisan s strani prodajalca, ni viden niti prodajalcu niti javno (kupci še vedno dostopajo)

### Rating
Entiteta za ocene objav s strani kupcev.

**Polja:**
- `id` (Long) - primarni ključ
- `postId` (Long) - ID objave
- `buyerId` (String) - ID kupca
- `rating` (Integer) - ocena 1-5
- `createdAt` (LocalDateTime) - čas oddaje ocene

**Unique constraint:** kombinacija (postId, buyerId) - vsak kupec lahko oceni objavo samo enkrat.

## Repositories

### PostsRepo
- `findBySellerIdExcludingUserDeleted` - prodajalčeve objave (ACTIVE + DISABLED)
- `findBoughtPostsByBuyer` - kupčevi nakupi (vsi statusi)
- `findByTagAndStatusActive` - javno iskanje po oznaki (samo ACTIVE)
- `findByStatus` - vse objave z določenim statusom
- `findByMultipleTagsActive` - iskanje po več oznakah (za priporočila)
- `findByTitleContainingIgnoreCaseAndStatus` - iskanje po naslovu (case-insensitive, s statusom)
- `findByTitleContainingIgnoreCase` - iskanje po naslovu (vsi statusi, za interno uporabo)
- `findByAzBlobNameContainingIgnoreCaseAndStatus` - iskanje po imenu datoteke (s statusom)
- `findByAzBlobNameContainingIgnoreCase` - iskanje po imenu datoteke (vsi statusi, za Azure dostop)

### RatingRepo
- `findByPostIdAndBuyerId` - najdi oceno določenega kupca za objavo
- `findByPostId` - vse ocene za objavo
- `averageForPost` - povprečna ocena objave
- `countForPost` - število ocen za objavo

## Service Layer (PostsServ)

### Upravljanje objav
- **`createPost(Posts post)`** - ustvari novo objavo (privzeto ACTIVE)
- **`changePostStatus(Long postId, String sellerId, Status newStatus)`** - spremeni status objave
- **`editPost(Long postId, String sellerId, Posts updates)`** - uredi osnovne podatke (naslov, opis, oznake, preview slike, cena, kopije)
- **`updatePostFile(Long postId, String sellerId, String newBlobName)`** - posodobi glavno datoteko (poveča fileVersion)

### Iskanje in filtriranje
- **`getPostsBySeller(String sellerId, Pageable)`** - prodajalčeve objave (brez USER_DELETED)
- **`getPostsByBuyer(String buyerId, Pageable)`** - kupčevi nakupi (vsi statusi)
- **`getPostsByTag(Tag tag, Pageable)`** - javne objave po oznaki (ACTIVE)
- **`getPostsByThemes(Tag t1, Tag t2, Tag t3, Pageable)`** - objave po temah/interesih (za feed)
- **`getPostInfo(Long postId)`** - podrobnosti objave
- **`searchByTitle(String title, Pageable)`** - iskanje po naslovu (ACTIVE)
- **`searchByTitleAnyStatus(String title, Pageable)`** - iskanje po naslovu (vsi statusi)
- **`searchByBlobName(String blobName, Pageable)`** - iskanje po Azure blob imenu (ACTIVE)
- **`searchByBlobNameAnyStatus(String blobName, Pageable)`** - iskanje po blob imenu (vsi statusi)

### Nakup in dostopnost
- **`addBuyer(Long postId, String buyerId)`** - dodaj kupca (idempotentno)
  - Preveri status (ne dovoli USER_DELETED)
  - Preveri razpoložljivost (kopije > 0 ali -1)
  - Zmanjša število kopij, če je > 0
  - Če je kupec že v seznamu, ne spremeni ničesar
- **`checkAvailability(Long postId)`** - preveri razpoložljivost (status + kopije)

### Ocene
- **`submitRating(Long postId, String buyerId, int ratingValue)`** - oddaj oceno (1-5)
  - Samo kupci lahko ocenjujejo
  - Idempotentno (posodobi če ocena že obstaja)
- **`getRatings(Long postId)`** - seznam vseh ocen za objavo
- **`getRatingSummary(Long postId)`** - povprečje in število ocen

## DTO-ji

### Availability
```java
{
  boolean available,
  Integer copies,
  Status status
}
```

### RatingSummary
```java
{
  double average,
  long count
}
```

## Logika kopij
- **-1**: neomejeno kopij (digitalni izdelek brez omejitve)
- **0**: razprodano (ni možen nakup)
- **>0**: število razpoložljivih kopij (ob nakupu se zmanjša)

## Status flow
1. **Ustvarjanje**: objava se ustvari s statusom ACTIVE
2. **Prodajalec lahko:**
   - Spremeni na DISABLED (začasno skrije)
   - Spremeni nazaj na ACTIVE (ponovno prikaže)
   - Spremeni na USER_DELETED ("izbriše" objavo)
3. **USER_DELETED:**
   - Prodajalec ne vidi objave med svojimi
   - Kupci še vedno dostopajo do nakupljene vsebine
   - Ni mogoč nov nakup
   - Ni vidna javno

## Azure Blob Storage Integration (AzureBlobServ)

### Metode
- **`uploadFile(MultipartFile file)`** - naloži datoteko iz HTTP uploada, vrne unikaten blob name (UUID + extension)
- **`uploadFile(InputStream, long size, String contentType, String filename)`** - naloži iz InputStreama
- **`getBlobMetadata(String blobName)`** - pridobi metapodatke (ime, tip, velikost, čas ustvarjanja)
- **`downloadBlob(String blobName)`** - prenesi blob kot InputStream
- **`generateSasUrl(String blobName, int expirationMinutes)`** - generira časovno omejeno SAS URL povezavo za prenos
- **`deleteBlob(String blobName)`** - izbriše blob iz storage-a
- **`blobExists(String blobName)`** - preveri obstoj bloba

### BlobMetadata DTO
```java
{
  String name,
  String contentType,
  long sizeBytes,
  OffsetDateTime createdAt,
  OffsetDateTime lastModified,
  String getSizeFormatted() // formatira velikost (B, KB, MB, GB)
}
```

### Konfiguracija
V `application.properties`:
```properties
azure.storage.connection-string=${AZURE_CONNECTION_STRING}
azure.storage.container-name=${AZURE_CONTAINER_NAME}
```

Nastavi environment variable:
- `AZURE_CONNECTION_STRING` - connection string iz Azure portala
- `AZURE_CONTAINER_NAME` - ime container-ja (npr. "slopeoasis-files")

### Workflow za upload posta
1. Frontend pošlje datoteko na POST endpoint
2. Controller pokliče `azureBlobServ.uploadFile(file)` → dobimo `blobName`
3. Controller kreira `Posts` z `azBlobName = blobName`
4. `PostsServ.createPost(post)` shrani v bazo
5. Za prenos: generiraš SAS URL z `generateSasUrl(blobName, 60)` → link velja 60 minut

## Opombe
- Iskanje po naslovih in Azure blob imenih uporablja `LIKE %term%` (case-insensitive)
- Vse operacije z nakupom in urejanjem preverjajo lastništvo (sellerId)
- FileVersion se avtomatsko poveča ob posodobitvi glavne datoteke
- LastTimeModified se avtomatsko posodobi ob vsaki spremembi entitete (Hibernate @UpdateTimestamp)
- Azure blob imena so UUID-ji (unikatni) + original extension
- SAS URL-ji omogočajo časovno omejene prenose brez izpostavljanja storage credentialov
