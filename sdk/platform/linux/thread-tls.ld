/* Expose the executable's PT_TLS template to the freestanding PAL adapter. */
SECTIONS
{
  .cplus_tls_metadata (INFO) :
  {
    __cplus_tls_image_start = ABSOLUTE(ADDR(.tdata));
    __cplus_tls_data_size = ABSOLUTE(SIZEOF(.tdata));
    __cplus_tls_image_end = ABSOLUTE(ADDR(.tbss) + SIZEOF(.tbss));
    __cplus_tls_alignment = ABSOLUTE(MAX(ALIGNOF(.tdata), ALIGNOF(.tbss)));
  }
}
INSERT AFTER .tbss;
