-- 1.12 : modes de transport BUS et TRAIN (PDF 9.2). La liste fermée reste vérifiée par la base.
alter table public.trips drop constraint trips_mode_check;
alter table public.trips add constraint trips_mode_check
  check (mode in ('WALK', 'CAR', 'TAP_TAP', 'TAXI', 'MOTO', 'BUS', 'TRAIN'));
