# FloatMap

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/75c085ac74a34d95b410b5fbb773b691)](https://app.codacy.com/app/gowgos5/FloatMap?utm_source=github.com&utm_medium=referral&utm_content=gowgos5/FloatMap&utm_campaign=Badge_Grade_Dashboard)

A supplement to the Grab Driver app. Essentially a map overlay that displays over the job cards to provide more information regarding the pick-up and drop-off locations.

WIP:
1) Improve accuracy of locations read. Currently, postal codes are used to pinpoint the locations, and the locations shown are occassionally wrong, especially in newer estates (case in point: Punggol) - Fixed?; used block + street instead for drop-off locations; more testings needed.
2) Store the drop-off location to allow for early drop-off (*hint hint*)

<br>
<p align="center">
  <img src="https://user-images.githubusercontent.com/9738454/59056743-7d186480-88cb-11e9-85bf-0f25ee4093b1.png" alt="Main Activity" width="250"/>
  <img src="https://user-images.githubusercontent.com/9738454/59056969-e9936380-88cb-11e9-8e43-d2fd3f5b045a.png" alt="Foreground Service" width="250"/>
  <img src="https://user-images.githubusercontent.com/9738454/59056976-eb5d2700-88cb-11e9-9229-72ee153293c4.png" alt="Map Overlay" width="250"/>
</p>
