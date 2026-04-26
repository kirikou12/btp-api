delete from supplier_invoice_images
where image_url !~* '^(https?://|/api/uploads/|[[:alnum:]_-]+/)'
  and image_url !~* '\.(jpg|jpeg|png|webp|heic)(\?.*)?$';

delete from worker_payment_images
where image_url !~* '^(https?://|/api/uploads/|[[:alnum:]_-]+/)'
  and image_url !~* '\.(jpg|jpeg|png|webp|heic)(\?.*)?$';
